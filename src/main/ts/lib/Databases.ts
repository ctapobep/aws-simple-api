import {Duration, RemovalPolicy} from 'aws-cdk-lib';
import {Construct} from 'constructs';
import * as rds from "aws-cdk-lib/aws-rds";
import {DatabaseClusterEngine, ParameterGroup, ServerlessCluster} from "aws-cdk-lib/aws-rds";
import {DatabaseInfo} from "./DatabaseInfo";
import {NetworkInfo} from "./NetworkInfo";
import {Port, SecurityGroup, SubnetType, Vpc} from "aws-cdk-lib/aws-ec2";
import {Secret} from "aws-cdk-lib/aws-secretsmanager";
import {Lambdas} from "./Lambdas";
import {Trigger} from "aws-cdk-lib/triggers";

export class Databases {
    static postgresAurora(scope: Construct, networkInfo: NetworkInfo,
                          dbSg: SecurityGroup, dbClientSg: SecurityGroup): DatabaseInfo {
        const cluster = Databases.dbCluster(scope, networkInfo, dbSg);
        const appSecret = this.appUserSecret(cluster);
        const dbInfo = {
            dbArn: cluster.clusterArn,
            clusterEndpoint: cluster.clusterEndpoint,
            adminSecret: cluster.secret!,
            appSecret
        };
        this.triggerInitScripts(cluster, networkInfo, dbInfo, dbClientSg);
        return dbInfo;
    }

    private static triggerInitScripts(cluster: ServerlessCluster, networkInfo: NetworkInfo,
                                      dbInfo: DatabaseInfo, dbClientSg :SecurityGroup) {
        new Trigger(cluster, 'InitDbTriggerForLambda', {
            handler: Lambdas.initDb(cluster, dbInfo, networkInfo, [dbClientSg]),
            executeAfter: [cluster]
        });
    }

    private static dbCluster(scope: Construct, props: NetworkInfo, sg: SecurityGroup): ServerlessCluster {
        return new rds.ServerlessCluster(scope, "DB", {
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
            enableDataApi: true,
            removalPolicy: RemovalPolicy.DESTROY
        });
    }

    static clientSecurityGroup(scope: Construct, vpc: Vpc): SecurityGroup {
        return new SecurityGroup(scope, 'DB-Client-SG', {vpc});
    }

    static dbSecurityGroup(scope: Construct, vpc: Vpc, allowInboundFor: SecurityGroup): SecurityGroup {
        const sg = new SecurityGroup(scope, 'DB-SG', {vpc});
        sg.addIngressRule(allowInboundFor, Port.tcp(5432));
        return sg
    }

    private static appUserSecret(cluster: ServerlessCluster): Secret {
        return new Secret(cluster, 'DB-AppUserSecret', {
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
    }
}