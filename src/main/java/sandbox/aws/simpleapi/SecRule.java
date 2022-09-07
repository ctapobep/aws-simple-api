package sandbox.aws.simpleapi;

public class SecRule {
    final int from, to;
    final String protocol;

    public SecRule(int ip) {
        this(ip, ip);
    }
    public SecRule(int from, int to) {
        this("tcp", from, to);
    }
    public SecRule(String protocol, int from, int to) {
        this.from = from;
        this.to = to;
        this.protocol = protocol;
    }

    public static SecRule icmp() {
        return new SecRule("icmp", -1, -1);
    }
    public static SecRule tcp(int ip) {
        return new SecRule(ip);
    }
    public static SecRule tcp(int fromIp, int toIp) {
        return new SecRule(fromIp, toIp);
    }
}
