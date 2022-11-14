package sandbox.aws.simpleapi;

/**
 * Each lib that works with Subnets (ec2, rds, etc) has its own class. This one is created to allow re-using
 * objects between different methods - those that work with diff services.
 */
public class Subnet {
    final String id;

    public Subnet(String id) {
        this.id = id;
    }
}
