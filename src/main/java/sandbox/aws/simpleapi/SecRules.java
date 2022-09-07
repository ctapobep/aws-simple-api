package sandbox.aws.simpleapi;

import software.amazon.awssdk.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import software.amazon.awssdk.services.ec2.model.IpPermission;
import software.amazon.awssdk.services.ec2.model.IpRange;

public class SecRules {
    private final String cidrIp4, description;
    private final SecRule[] rules;

    public SecRules(String cidrIp4, String description, int port) {
        this(cidrIp4, description, new SecRule(port));
    }
    public SecRules(String cidrIp4, String description, SecRule... rules) {
        this.cidrIp4 = cidrIp4;
        this.description = description;
        this.rules = rules;
    }

    AuthorizeSecurityGroupIngressRequest.Builder toAuthorizeIngress() {
        IpPermission[] permissions = new IpPermission[rules.length];
        for (int i = 0; i < rules.length; i++)
            permissions[i] = IpPermission.builder()
                    .ipProtocol(rules[i].protocol).fromPort(rules[i].from).toPort(rules[i].to)
                    .ipRanges(IpRange.builder().cidrIp(cidrIp4).build())
                    .build();
        return AuthorizeSecurityGroupIngressRequest.builder().ipPermissions(permissions);
    }
}
