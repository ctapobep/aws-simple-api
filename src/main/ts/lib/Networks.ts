import {SubnetType, Vpc} from "aws-cdk-lib/aws-ec2";
import {Construct} from "constructs";
import {NetworkInfo} from "./NetworkInfo";

export class Networks {
    static with2Subnets(scope: Construct): NetworkInfo {
        const vpcId = "VPC";
        const vpc = new Vpc(scope, vpcId, {
            maxAzs: 2,
            subnetConfiguration: [this.privateSubnetWithEgress(vpcId), this.publicSubnet(vpcId)]
        });
        return {vpc};
    }

    private static publicSubnet(idPrefix: string) {
        return {
            cidrMask: 24,
            name: idPrefix + '-public',
            subnetType: SubnetType.PUBLIC
        };
    }

    /**
     * Outbound Internet connectivity is needed to access Secrets Manager as its endpoints reside outside
     * of the private subnet. There are ways of doing this w/o NAT and public internet access, but we may
     * need it anyway at some point e.g. to access some public Internet resources.
     */
    private static privateSubnetWithEgress(idPrefix: string) {
        return {
            cidrMask: 24,
            name: idPrefix + '-private',
            subnetType: SubnetType.PRIVATE_WITH_EGRESS //Creates NAT Gateway for outbound Internet connections.
        };
    }
}