#!/usr/bin/env node
import 'source-map-support/register';
import {Databases} from '../lib/Databases';
import {Networks} from "../lib/Networks";
import {App, Stack} from "aws-cdk-lib";

const app = new App();
const mainStack = new Stack(app, 'HelloWorld');

const networkInfo = Networks.with2Subnets(mainStack);
const dbClientSg = Databases.clientSecurityGroup(mainStack, networkInfo.vpc);
const dbSg = Databases.dbSecurityGroup(mainStack, networkInfo.vpc, dbClientSg);

const dbInfo = Databases.postgresAurora(mainStack, networkInfo, dbSg, dbClientSg);

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