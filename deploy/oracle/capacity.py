"""Read-only OCI inventory and capacity. Never launches a VM or edits SupplyPrint.

Use a private venv containing oci. Credentials stay in ~/.oci/config.
"""
import argparse
import datetime as dt
import hashlib
import json
import pathlib


REGION = "ap-hyderabad-1"
A1 = "VM.Standard.A1.Flex"
E2 = "VM.Standard.E2.1.Micro"


def check(config="~/.oci/config", profile="DEFAULT", supply=None):
    import oci
    cfg = oci.config.from_file(str(pathlib.Path(config).expanduser()), profile)
    cfg["region"] = REGION
    options = {"retry_strategy": oci.retry.NoneRetryStrategy(), "timeout": (10, 30)}
    iam = oci.identity.IdentityClient(cfg, **options)
    compute = oci.core.ComputeClient(cfg, **options)
    storage = oci.core.BlockstorageClient(cfg, **options)
    network = oci.core.VirtualNetworkClient(cfg, **options)
    tenancy = cfg["tenancy"]
    home = next(x.region_name for x in iam.list_region_subscriptions(tenancy).data if x.is_home_region)
    if home != REGION:
        raise ValueError("Home region differs from the approved Hyderabad region")
    compartments = [tenancy] + [x.id for x in oci.pagination.list_call_get_all_results(
        iam.list_compartments, tenancy, compartment_id_in_subtree=True,
        access_level="ANY", lifecycle_state="ACTIVE").data]
    domains = iam.list_availability_domains(tenancy).data
    instances, volumes, vcns, subnets = [], [], [], []
    for compartment in compartments:
        instances += [x for x in oci.pagination.list_call_get_all_results(
            compute.list_instances, compartment).data if x.lifecycle_state != "TERMINATED"]
        volumes += [x for x in oci.pagination.list_call_get_all_results(
            storage.list_volumes, compartment_id=compartment).data if x.lifecycle_state != "TERMINATED"]
        for domain in domains:
            volumes += [x for x in oci.pagination.list_call_get_all_results(storage.list_boot_volumes,
                compartment_id=compartment, availability_domain=domain.name).data
                if x.lifecycle_state != "TERMINATED"]
        vcns += oci.pagination.list_call_get_all_results(network.list_vcns, compartment).data
        subnets += oci.pagination.list_call_get_all_results(network.list_subnets, compartment).data
    shapes = oci.pagination.list_call_get_all_results(compute.list_shapes, tenancy,
        availability_domain=domains[0].name).data
    capacities = []
    for domain in domains:
        for shape in (A1, E2):
            if not any(x.shape == shape for x in shapes):
                continue
            item = {"instance_shape": shape}
            if shape == A1:
                item["instance_shape_config"] = oci.core.models.CapacityReportInstanceShapeConfig(
                    ocpus=2, memory_in_gbs=12)
            try:
                report = compute.create_compute_capacity_report(oci.core.models.CreateComputeCapacityReportDetails(
                    compartment_id=tenancy, availability_domain=domain.name, shape_availabilities=[
                        oci.core.models.CreateCapacityReportShapeAvailabilityDetails(**item)])).data
                capacities += [{"shape": x.instance_shape, "domain": domain.name,
                    "status": x.availability_status} for x in report.shape_availabilities]
            except oci.exceptions.ServiceError as error:
                capacities.append({"shape": shape, "domain": domain.name,
                    "status": "CHECK_FAILED", "error_code": error.code, "http_status": error.status})
    network_rules = []
    for subnet in subnets:
        for rule_id in subnet.security_list_ids:
            rule = network.get_security_list(rule_id).data
            network_rules += [{"source": item.source, "protocol": item.protocol,
                "tcp_ports": oci.util.to_dict(item.tcp_options.destination_port_range) if item.tcp_options and item.tcp_options.destination_port_range else None}
                for item in rule.ingress_security_rules]
    images = {}
    for shape in (A1, E2):
        choices = oci.pagination.list_call_get_all_results(compute.list_images, tenancy,
            operating_system="Canonical Ubuntu", operating_system_version="24.04", shape=shape,
            sort_by="TIMECREATED", sort_order="DESC").data
        choices = [x for x in choices if x.compartment_id is None and x.lifecycle_state == "AVAILABLE"]
        images[shape] = {"official_ubuntu_available": bool(choices),
            "shape_compatible": bool(choices and compute.get_image_shape_compatibility_entry(choices[0].id, shape).data.shape == shape)}
    result = {"checked_at": dt.datetime.now(dt.timezone.utc).isoformat(), "home_region": home,
        "tenancy_sha256": hashlib.sha256(tenancy.encode()).hexdigest(),
        "account_type": "USER_CONFIRMED_FREE_TIER_NOT_PAYG; IAM does not return billing plan",
        "a1_ocpus": sum(x.shape_config.ocpus for x in instances if x.shape == A1),
        "a1_ram_gb": sum(x.shape_config.memory_in_gbs for x in instances if x.shape == A1),
        "storage_gb": sum(x.size_in_gbs for x in volumes),
        "instances": [{"id": x.id, "shape": x.shape, "state": x.lifecycle_state} for x in instances],
        "volumes": [{"id": x.id, "size_gb": x.size_in_gbs, "state": x.lifecycle_state} for x in volumes],
        "free_shapes": [{"shape": x.shape, "billing_type": x.billing_type} for x in shapes if x.shape in (A1, E2)],
        "vcns": [{"id": x.id, "state": x.lifecycle_state} for x in vcns],
        "subnets": [{"id": x.id, "vcn_id": x.vcn_id, "state": x.lifecycle_state} for x in subnets],
        "capacity": capacities, "network_ingress": network_rules, "ubuntu_images": images,
        "resources_created": []}
    if supply:
        state = json.loads((pathlib.Path(supply) / "deploy/oracle/state/provisioned.json").read_text())
        expected = {x["type"]: x["id"] for x in state["resources"]}
        result["saved_network_verified"] = (expected.get("vcn") in {x.id for x in vcns}
            and expected.get("public-subnet") in {x.id for x in subnets})
        result["supplyprint_state"] = state["deployment"]
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", default="~/.oci/config")
    parser.add_argument("--profile", default="DEFAULT")
    parser.add_argument("--supplyprint", type=pathlib.Path)
    parser.add_argument("--output", type=pathlib.Path, default=pathlib.Path(__file__).parent / "state/inventory.json")
    args = parser.parse_args()
    result = check(args.config, args.profile, args.supplyprint)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    # Console summary deliberately excludes resource IDs and credentials.
    print(json.dumps({k: result[k] for k in ("checked_at", "home_region", "a1_ocpus", "a1_ram_gb",
        "storage_gb", "capacity", "resources_created")}, indent=2))


if __name__ == "__main__":
    main()
