package sandbox.aws.simpleapi;

import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.List;
import java.util.Stack;

import static sandbox.aws.simpleapi.Retry.retry;

public class Ec2 {
    private final Ec2Client ec2;
    private final Stack<Ec2Response> responses = new Stack<>();

    public Ec2(Ec2Client ec2) {
        this.ec2 = ec2;
    }
    public Vpc createVpc(String vpcName, boolean enableDnsHostNames) {
        Vpc vpc = resp(ec2.createVpc(CreateVpcRequest.builder()
                .cidrBlock("10.0.0.0/16")
                .tagSpecifications(vpcTag("Name", vpcName))
                .build())).vpc();
        if(enableDnsHostNames)
            ec2.modifyVpcAttribute(ModifyVpcAttributeRequest.builder().vpcId(vpc.vpcId()).enableDnsHostnames(bool(true)).build());
        return vpc;
    }
    public void deleteCreatedResources() {
        System.out.println("Cleaning up resources that were created");
        while (!responses.isEmpty()) {
            Ec2Response resp = responses.pop();
            System.out.println("Deleting: " + resp);
            if (resp instanceof CreateSubnetResponse r) {
                ec2.deleteSubnet(DeleteSubnetRequest.builder().subnetId(r.subnet().subnetId()).build());
            } else if (resp instanceof CreateVpcResponse r) {
                ec2.deleteVpc(DeleteVpcRequest.builder().vpcId(r.vpc().vpcId()).build());
            }
            else if (resp instanceof CreateRouteTableResponse r) {
                String id = r.routeTable().routeTableId();
                RouteTable rt = ec2.describeRouteTables(DescribeRouteTablesRequest.builder().routeTableIds(id).build()).routeTables().get(0);
                for (RouteTableAssociation a : rt.associations())
                    ec2.disassociateRouteTable(DisassociateRouteTableRequest.builder()
                            .associationId(a.routeTableAssociationId()).build());
                ec2.deleteRouteTable(DeleteRouteTableRequest.builder().routeTableId(id).build());
            } else if (resp instanceof CreateNatGatewayResponse r) {
                releaseIps(deleteNatGateway(r.natGateway()));
            } else if(resp instanceof CreateInternetGatewayResponse r) {
                String igwId = r.internetGateway().internetGatewayId();
                DescribeInternetGatewaysResponse igwResp = ec2.describeInternetGateways(DescribeInternetGatewaysRequest.builder().internetGatewayIds(igwId).build());
                for (InternetGatewayAttachment a : igwResp.internetGateways().get(0).attachments())
                    ec2.detachInternetGateway(DetachInternetGatewayRequest.builder()
                            .internetGatewayId(igwId)
                            .vpcId(a.vpcId()).build());
                ec2.deleteInternetGateway(DeleteInternetGatewayRequest.builder().internetGatewayId(igwId).build());
            } else if(resp instanceof CreateSecurityGroupResponse r)
                deleteSecurityGroup(r.groupId());
            else if(resp instanceof RunInstancesResponse r)
                ec2.stopInstances(StopInstancesRequest.builder()
                        .instanceIds(r.instances().get(0).instanceId())
                        .build());
            else
                throw new IllegalStateException("Forgot to add a delete procedure? " + resp);
        }
    }
    public Instance startInstance(String amiId, InstanceType type, Subnet subnet, String securityGroupId,
                                  String keyname, String name) {
        return resp(ec2.runInstances(RunInstancesRequest.builder()
                .imageId(amiId)
                .instanceType(type)
                .subnetId(subnet.id)
                .securityGroupIds(securityGroupId)
                .minCount(1).maxCount(1)
                .keyName(keyname)
                .tagSpecifications(ec2Tag(ResourceType.INSTANCE, "Name", name))
                .build())).instances().get(0);
    }

    private List<String> deleteNatGateway(NatGateway gw) {
        String id = gw.natGatewayId();
        List<String> allocatedIps = ec2
                .describeNatGateways(DescribeNatGatewaysRequest.builder().natGatewayIds(id).build())
                .natGateways().get(0)
                .natGatewayAddresses().stream().map(NatGatewayAddress::allocationId).toList();
        DeleteNatGatewayRequest deleteReq = DeleteNatGatewayRequest.builder().natGatewayId(id).build();
        ec2.deleteNatGateway(deleteReq);
        waitForStatus(gw, NatGatewayState.DELETED);
        return allocatedIps;
    }
    private void releaseIps(List<String> allocatedIps) {
        for (String addr: allocatedIps)
            ec2.releaseAddress(ReleaseAddressRequest.builder().allocationId(addr).build());
    }
    public String createSecGroup(Vpc vpc, String name, String description, List<SecRules> ingressRules) {
        String gId = resp(ec2.createSecurityGroup(
                CreateSecurityGroupRequest.builder()
                        .groupName(name)
                        .description(description)
                        .tagSpecifications(ec2Tag(ResourceType.SECURITY_GROUP, "Name", name))
                        .vpcId(vpc.vpcId()).build())
        ).groupId();

        for (SecRules rule : ingressRules)
            ec2.authorizeSecurityGroupIngress(rule.toAuthorizeIngress().groupId(gId).build());
        return gId;
    }
    public void deleteSecurityGroup(String secGroupId) {
        ec2.deleteSecurityGroup(DeleteSecurityGroupRequest.builder().groupId(secGroupId).build());
    }
    public void addRoute(RouteTable rt, String dstCidr, InternetGateway igw) {
        ec2.createRoute(CreateRouteRequest.builder()
                .routeTableId(rt.routeTableId())
                .destinationCidrBlock(dstCidr)
                .gatewayId(igw.internetGatewayId())
                .build());
    }
    public void addRoute(RouteTable rt, String dstCidr, NatGateway gw) {
        ec2.createRoute(CreateRouteRequest.builder()
                .routeTableId(rt.routeTableId())
                .destinationCidrBlock(dstCidr)
                .natGatewayId(gw.natGatewayId())
                .build());
    }
    public NatGateway createNatGateway(Subnet subnet, String name, boolean pub) {
        String allocationId = null;
        if(pub)
            allocationId = ec2.allocateAddress(AllocateAddressRequest.builder()
                    .domain(DomainType.VPC)
                    .build()).allocationId();
        NatGateway gw = resp(ec2.createNatGateway(CreateNatGatewayRequest.builder()
                .subnetId(subnet.id)
                .connectivityType(ConnectivityType.PUBLIC)
                .allocationId(allocationId)
                .tagSpecifications(ec2Tag(ResourceType.NATGATEWAY, "Name", name))
                .build())).natGateway();
        waitForStatus(gw, NatGatewayState.AVAILABLE);
        return gw;
    }

    private void waitForStatus(NatGateway gw, NatGatewayState waitFor) {
        retry("creating " + gw, (c) -> {
            if(c.getRetryCount() > 0)
                System.out.println("Updating NAT, still not "+waitFor+". Waiting #" + c.getRetryCount());
            NatGateway status = ec2.describeNatGateways(DescribeNatGatewaysRequest.builder().natGatewayIds(gw.natGatewayId()).build()).natGateways().get(0);
            if (status.state() != waitFor)
                throw new IllegalStateException("Still pending");
            return status;
        });
    }

    InternetGateway createInternetGateway(Vpc vpc, String name) {
        InternetGateway igw = resp(ec2.createInternetGateway(CreateInternetGatewayRequest.builder()
                .tagSpecifications(ec2Tag(ResourceType.INTERNET_GATEWAY, "Name", name))
                .build())).internetGateway();
        ec2.attachInternetGateway(AttachInternetGatewayRequest.builder()
                .vpcId(vpc.vpcId())
                .internetGatewayId(igw.internetGatewayId())
                .build());
        return igw;
    }
    public RouteTable createRouteTable(Vpc vpc, String rtName, Subnet... associateWith) {
        RouteTable rt = resp(ec2.createRouteTable(CreateRouteTableRequest.builder()
                .vpcId(vpc.vpcId())
                .tagSpecifications(ec2Tag(ResourceType.ROUTE_TABLE, "Name", rtName))
                .build())).routeTable();
        for (Subnet subnet : associateWith) {
            ec2.associateRouteTable(AssociateRouteTableRequest.builder()
                    .routeTableId(rt.routeTableId())
                    .subnetId(subnet.id).build());
        }
        return rt;
    }
    public Subnet createSubnet(Vpc vpc, String az, String cidr, boolean pub) {
        String pubPrivPrefix = pub ? "-Pub" : "-Priv";
        Subnet subnet = new Subnet(resp(ec2.createSubnet(CreateSubnetRequest.builder()
                .vpcId(vpc.vpcId())
                .cidrBlock(cidr)
                .tagSpecifications(subnetTag("Name", name(vpc.tags()) + pubPrivPrefix))
                .availabilityZone(az)
                .build())).subnet().subnetId());
        if (pub)
            ec2.modifySubnetAttribute(ModifySubnetAttributeRequest.builder()
                    .subnetId(subnet.id)
                    .mapPublicIpOnLaunch(bool(pub)).build());
        return subnet;
    }
    private <T extends Ec2Response> T resp(T resp) {
        System.out.println(resp);
        this.responses.add(resp);
        return resp;
    }

    private static TagSpecification subnetTag(String name, String value) {
        return ec2Tag(ResourceType.SUBNET, name, value);
    }
    private static TagSpecification vpcTag(String name, String value) {
        return ec2Tag(ResourceType.VPC, name, value);
    }
    private static TagSpecification ec2Tag(ResourceType resourceType, String name, String value) {
        return TagSpecification.builder().resourceType(resourceType).tags(
                Tag.builder().key(name).value(value).build()
        ).build();
    }
    private static AttributeBooleanValue bool(boolean b) {
        return AttributeBooleanValue.builder().value(b).build();
    }
    private static String name(Iterable<Tag> tags) {
        for (Tag tag : tags)
            if("Name".equals(tag.key()))
                return tag.value();
        return null;
    }
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
