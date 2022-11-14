package sandbox.aws.simpleapi;

import software.amazon.awssdk.services.ec2.model.InternetGateway;
import software.amazon.awssdk.services.ec2.model.RouteTable;
import software.amazon.awssdk.services.ec2.model.Vpc;

public class Scenarios {
    private final Ec2 ec2;
    private final Rds rds;
    private final Ecs ecs;
    private final String[] azs;

    public Scenarios(Ec2 ec2, Rds rds, Ecs ecs, String[] azs) {
        this.ec2 = ec2;
        this.rds = rds;
        this.ecs = ecs;
        if(azs == null || azs.length == 0)
            throw new IllegalArgumentException("" + azs);
        this.azs = azs;
    }

    public Vpc vpcWith2Subnets(String vpcName) {
        Vpc vpc = ec2.createVpc(vpcName, true);
        // PUBLIC
        Subnet pub1a = ec2.createSubnet(vpc, azs[0], "10.0.0.0/24", true);
        RouteTable publicRt = ec2.createRouteTable(vpc, vpcName + "-Pub", pub1a);
        InternetGateway igw = ec2.createInternetGateway(vpc, vpcName + "-IGW");
        ec2.addRoute(publicRt, "0.0.0.0/0", igw);

        // PRIVATE
        Subnet priv1a = ec2.createSubnet(vpc, azs[0], "10.0.128.0/24", false);
        return vpc;
    }
}
