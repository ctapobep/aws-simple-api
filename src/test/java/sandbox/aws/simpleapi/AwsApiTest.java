package sandbox.aws.simpleapi;

import org.junit.After;
import org.junit.Test;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.InternetGateway;
import software.amazon.awssdk.services.ec2.model.NatGateway;
import software.amazon.awssdk.services.ec2.model.RouteTable;
import software.amazon.awssdk.services.ec2.model.Vpc;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.DBInstance;

import java.util.List;

import static sandbox.aws.simpleapi.SecRule.icmp;
import static sandbox.aws.simpleapi.SecRule.tcp;
import static software.amazon.awssdk.services.ec2.model.InstanceType.T3_MICRO;


public class AwsApiTest {
    public Region region;
    public String azA, azB;
    private Ec2 ec2;
    private Rds rds;

    @After public void deleteCreatedResources() {
        if(rds != null)
            rds.deleteCreatedResources();
        ec2.deleteCreatedResources();
    }

    @Test public void vtlb() {
        ec2 = new Ec2(ec2());
        Scenarios quick = new Scenarios(ec2, null, null, null);
        String vpcName = "vtlb";
        Vpc vpc = quick.vpcWith2Subnets(vpcName);
        String pubAccessSecGr = ec2.createSecGroup(vpc, "Public Access", "Web API public access", List.of(
                new SecRules("0.0.0.0/0", "Web ports", tcp(80), tcp(22), icmp())
        ));
        // PRIVATE
        Subnet priv1a = ec2.createSubnet(vpc, azA, "10.0.128.0/24", false);
        Subnet priv1b = ec2.createSubnet(vpc, azB, "10.0.129.0/24", false);
        RouteTable privateRt = ec2.createRouteTable(vpc, vpcName + "-Priv", priv1a);
//
//        rds = new Rds(rdsClient());
//
//        DBInstance db = rds.createPgInstance(azA,
//                ec2.createSecGroup(vpc, "PostgreSQL", "Opens ports for PG", List.of(new SecRules("10.0.0.0/16", "PG port", tcp(5432)))),
//                List.of(priv1a, priv1b),
//                "vtlb", "postgres", "blahPG-pass1");
//
//        Ecs ecs = new Ecs(ecsClient());
//
//
//        rds.startDbInstance(db);
    }
    @Test public void autoscale() {
        ec2 = new Ec2(ec2());
        Scenarios quick = new Scenarios(ec2, null, null, new String[]{azA});
        String vpcName = "vtlb";
        Vpc vpc = quick.vpcWith2Subnets(vpcName);
        System.out.println(vpc);
//
//        for (int i = 0; i < 2; i++) {
//            ec2.startInstance("ami-078e13ebe3b027f1c", T3_MICRO, pub1a, pubAccessSecGr, "elsci.pub", vpcName + " Instance1");
//        }
    }
    @Test public void createVpc() {
        ec2 = new Ec2(ec2());
        String vpcName = "JavaSDK";
        Vpc vpc = ec2.createVpc(vpcName, true);

        Subnet pub1a = ec2.createSubnet(vpc, azA, "10.0.1.0/24", true);
        Subnet pub1b = ec2.createSubnet(vpc, azB, "10.0.2.0/24", true);
        Subnet priv1a = ec2.createSubnet(vpc, azA, "10.0.3.0/24", false);
        Subnet priv1b = ec2.createSubnet(vpc, azB, "10.0.4.0/24", false);
        RouteTable privateRt = ec2.createRouteTable(vpc, vpcName + "-PrivRouteTable", priv1a, priv1b);
        RouteTable publicRt = ec2.createRouteTable(vpc, vpcName + "-PubRouteTable", pub1a, pub1b);
        InternetGateway igw = ec2.createInternetGateway(vpc, vpcName + "-IGW");
        ec2.addRoute(publicRt, "0.0.0.0/0", igw);
        NatGateway natgw = ec2.createNatGateway(pub1a, vpcName + "-NatGW-Pub", true);
        ec2.addRoute(privateRt, "0.0.0.0/0", natgw);
        String securityGroup = ec2.createSecGroup(vpc, "Public Access", "Web API public access", List.of(
                new SecRules("0.0.0.0/0", "Web ports", tcp(3000, 9000), tcp(22), icmp())
        ));

        ec2.startInstance("ami-0440e5026412ff23f", T3_MICRO, pub1a, securityGroup, "KEY_NAME!", vpcName + " Instance1");
    }

    private Ec2Client ec2() {
        region = getRegion();
        azA = region.id() + "a";
        azB = region.id() + "b";
        return Ec2Client.builder()
                .credentialsProvider(getProfileCreds())
                .region(region)
                .build();
    }
    private RdsClient rdsClient() {
        return RdsClient.builder()
                .region(getRegion())
                .credentialsProvider(getProfileCreds())
                .build();
    }
    private EcsClient ecsClient() {
        return EcsClient.builder()
                .region(getRegion())
                .credentialsProvider(getProfileCreds())
                .build();
    }
    private static Region getRegion() {
        return Region.of(System.getProperty("region"));
    }
    private static ProfileCredentialsProvider getProfileCreds() {
        String awsProfile = System.getProperty("profile");
        if(awsProfile == null)
            throw new IllegalArgumentException("Pass -Dprofile=[name of aws profile]");
        return ProfileCredentialsProvider.create(awsProfile);
    }
}
