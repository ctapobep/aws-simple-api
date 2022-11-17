import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as apigw from 'aws-cdk-lib/aws-apigateway';
import {Construct} from "constructs";
import {RetentionDays} from "aws-cdk-lib/aws-logs";
import {DatabaseInfo} from "./DatabaseInfo";
import {Duration} from "aws-cdk-lib";
import {NetworkInfo} from "./NetworkInfo";
import {SecurityGroup, SubnetType} from "aws-cdk-lib/aws-ec2";

export class Lambdas {
    static initDb = function (scope: Construct,
                              dbProps: DatabaseInfo, networkProps: NetworkInfo, sgs: SecurityGroup[]): void {
        const scriptDir = 'Lambda-InitDb';
        new apigw.LambdaRestApi(scope, scriptDir + "-Proxy", {
            handler: Lambdas.createLambda(scope, scriptDir, dbProps, networkProps, sgs)
        });
    }

    private static createLambda = function (scope: Construct, scriptDir: string, props: DatabaseInfo,
                                            networkProps: NetworkInfo, sgs: SecurityGroup[]): lambda.Function {

        const fn = new lambda.Function(scope, scriptDir, {
            runtime: lambda.Runtime.NODEJS_16_X,
            code: lambda.Code.fromAsset(scriptDir),
            handler: 'lambda.handler',
            timeout: Duration.seconds(20),
            vpc: networkProps.vpc,
            vpcSubnets: networkProps.vpc.selectSubnets({subnetType: SubnetType.PRIVATE_WITH_EGRESS}),
            securityGroups: sgs,
            environment: {
                DB_ADMIN_SECRET_ARN: props.adminSecret.secretArn,
                DB_APP_SECRET_ARN: props.appSecret.secretArn
            },
            logRetention: RetentionDays.ONE_DAY
        });
        props.adminSecret.grantRead(fn);
        props.appSecret.grantRead(fn);
        return fn;
    }
}