package sandbox.aws.simpleapi;

import org.junit.After;
import org.junit.Test;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.List;

import static sandbox.aws.simpleapi.SecRule.icmp;
import static sandbox.aws.simpleapi.SecRule.tcp;
import static software.amazon.awssdk.services.ec2.model.InstanceType.T3_MICRO;


public class AwsApiTest {
    public Region region;
    public String azA, azB;
    private Ec2 api;

    @After public void deleteCreatedResources() {
        api.deleteCreatedResources();
    }

    @Test public void createVpc() {
        api = new Ec2(ec2());
        String vpcName = "JavaSDK";
        Vpc vpc = api.createVpc(vpcName, true);

        Subnet pub1a = api.createSubnet(vpc, azA, "10.0.1.0/24", true);
        Subnet pub1b = api.createSubnet(vpc, azB, "10.0.2.0/24", true);
        Subnet priv1a = api.createSubnet(vpc, azA, "10.0.3.0/24", false);
        Subnet priv1b = api.createSubnet(vpc, azB, "10.0.4.0/24", false);
        RouteTable privateRt = api.createRouteTable(vpc, vpcName + "-PrivRouteTable", priv1a, priv1b);
        RouteTable publicRt = api.createRouteTable(vpc, vpcName + "-PubRouteTable", pub1a, pub1b);
        InternetGateway igw = api.createInternetGateway(vpc, vpcName + "-IGW");
        api.addRoute(publicRt, "0.0.0.0/0", igw);
        NatGateway natgw = api.createNatGateway(pub1a, vpcName + "-NatGW-Pub", true);
        api.addRoute(privateRt, "0.0.0.0/0", natgw);
        String securityGroup = api.createSecurityGroup(vpc, "Public Access", "Web API public access", List.of(
                new SecRules("0.0.0.0/0", "Web ports", tcp(3000, 9000), tcp(22), icmp())
        ));

        api.startInstance("ami-0440e5026412ff23f", T3_MICRO, pub1a, securityGroup, "KEY_NAME!", vpcName + " Instance1");
    }

    private Ec2Client ec2() {
        String awsProfile = System.getProperty("profile");
        region = Region.of(System.getProperty("region"));
        azA = region.id() + "a";
        azB = region.id() + "b";
        if(awsProfile == null)
            throw new IllegalArgumentException("Pass -Dprofile=[name of aws profile]");
        return Ec2Client.builder()
                .credentialsProvider(ProfileCredentialsProvider.create(awsProfile))
                .region(region)
                .build();
    }
}
