import {Construct} from "constructs";
import {AwsCustomResource, AwsCustomResourcePolicy, PhysicalResourceId} from "aws-cdk-lib/custom-resources";
import {Effect, PolicyStatement} from "aws-cdk-lib/aws-iam";
import {Duration} from "aws-cdk-lib";
import * as lambda from 'aws-cdk-lib/aws-lambda';

/**
 * Could've used standard {triggers.TriggerFunction}, but its design is questionable - it <i>inherits</i>
 * {lambda.Function}. But what if we want the function to be triggered either using Rest API or during
 * deployment? So to be more universal - created this utility.
 */
export class Triggers {
    static onDeploy(scope: Construct, lambdaName: string, lambda: lambda.Function) {
        const triggerId = lambdaName + '-TriggerOnDeploy';
        new AwsCustomResource(scope, triggerId, {
            policy: AwsCustomResourcePolicy.fromStatements([new PolicyStatement({
                actions: ['lambda:InvokeFunction'],
                effect: Effect.ALLOW,
                resources: [lambda.functionArn]
            })]),
            timeout: Duration.minutes(1),
            onCreate: {
                service: 'Lambda',
                action: 'invoke',
                parameters: {
                    FunctionName: lambda.functionName,
                    InvocationType: 'Event'
                },
                physicalResourceId: PhysicalResourceId.of(triggerId/*let it be equal to the trigger name*/)
            }
        });
    }
}