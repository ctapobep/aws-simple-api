#!/usr/bin/env node
import 'source-map-support/register';
import {Databases} from '../lib/Databases';
import {Lambdas} from '../lib/Lambdas';
import {Networks} from "../lib/Networks";
import {App, Stack} from "aws-cdk-lib";

const app = new App();
const appStack = new Stack(app, 'HelloWorld');
// re-creating network takes ~5mins, so keeping it separate to optimize destroy-deploy cycle
const networkingStack = new Stack(app, 'HelloWorldNetwork');

const networkInfo = Networks.with2Subnets(networkingStack);
const dbClientSg = Databases.clientSecurityGroup(networkingStack, networkInfo.vpc);
const dbSg = Databases.dbSecurityGroup(networkingStack, networkInfo.vpc, dbClientSg);

const dbInfo = Databases.postgresAurora(appStack, networkInfo, dbSg);
Lambdas.initDb(appStack, dbInfo, networkInfo, [dbClientSg]);

// new RdsStack(app, 'TsStack', {
  /* If you don't specify 'env', this stack will be environment-agnostic.
   * Account/Region-dependent features and context lookups will not work,
   * but a single synthesized template can be deployed anywhere. */

  /* Uncomment the next line to specialize this stack for the AWS Account
   * and Region that are implied by the current CLI configuration. */
  // env: { account: process.env.CDK_DEFAULT_ACCOUNT, region: process.env.CDK_DEFAULT_REGION },

  /* Uncomment the next line if you know exactly what Account and Region you
   * want to deploy the stack to. */
  // env: { account: '123456789012', region: 'us-east-1' },

  /* For more information, see https://docs.aws.amazon.com/cdk/latest/guide/environments.html */
// });