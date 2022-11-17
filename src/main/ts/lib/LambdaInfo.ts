import {SecurityGroup} from "aws-cdk-lib/aws-ec2";

export interface LambdaInfo {
    securityGroups: SecurityGroup[];
}