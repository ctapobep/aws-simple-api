import {Duration, RemovalPolicy, SecretValue} from 'aws-cdk-lib';
import {Construct} from 'constructs';
import * as rds from "aws-cdk-lib/aws-rds";
import {DatabaseClusterEngine, ParameterGroup} from "aws-cdk-lib/aws-rds";
import {DatabaseInfo} from "./DatabaseInfo";
import {NetworkInfo} from "./NetworkInfo";
import {Port, SecurityGroup, SubnetType, Vpc} from "aws-cdk-lib/aws-ec2";
import {Secret} from "aws-cdk-lib/aws-secretsmanager";

export class Databases {
    static postgresAurora(scope: Construct, props: NetworkInfo, sg: SecurityGroup): DatabaseInfo {
        const cluster = new rds.ServerlessCluster(scope, "DB", {
            defaultDatabaseName: 'postgres',
            engine: DatabaseClusterEngine.AURORA_POSTGRESQL,
            vpc: props.vpc,
            vpcSubnets: props.vpc.selectSubnets({subnetType: SubnetType.PRIVATE_WITH_EGRESS}),
            securityGroups: [sg],
            parameterGroup: ParameterGroup.fromParameterGroupName(scope,
                'ParameterGroup',
                'default.aurora-postgresql10'
            ),
            scaling: {
                autoPause: Duration.hours(2), // must be removed for PRD usage
                minCapacity: 2, maxCapacity: 2
            },
            removalPolicy: RemovalPolicy.DESTROY
        });
        const appSecret = new Secret(scope, 'DB-AppUserSecret', {
            generateSecretString:{
                secretStringTemplate: JSON.stringify({
                    dbname: 'companyname',
                    username: 'helloworld',
                    host: cluster.clusterEndpoint.hostname,
                    port: cluster.clusterEndpoint.port
                }),
                generateStringKey: 'password',
                excludeCharacters: '/\\\'\"'
            }
        });
        return {
            dbArn: cluster.clusterArn,
            clusterEndpoint: cluster.clusterEndpoint,
            adminSecret: cluster.secret!,
            appSecret
        }
    }

    static clientSecurityGroup(scope: Construct, vpc: Vpc): SecurityGroup {
        return new SecurityGroup(scope, 'DB-Client-SG', {vpc});
    }

    static dbSecurityGroup(scope: Construct, vpc: Vpc, allowInboundFor: SecurityGroup): SecurityGroup {
        const sg = new SecurityGroup(scope, 'DB-SG', {vpc});
        sg.addIngressRule(allowInboundFor, Port.tcp(5432));
        return sg
    }
}