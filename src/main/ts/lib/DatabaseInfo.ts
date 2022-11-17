import {Endpoint} from "aws-cdk-lib/aws-rds";
import {ISecret} from "aws-cdk-lib/aws-secretsmanager";

export interface DatabaseInfo {
    readonly dbArn: string;
    readonly clusterEndpoint: Endpoint;
    readonly adminSecret: ISecret;
    /**
     * Creds to be used by the app itself to connect to the DB
     */
    readonly appSecret: ISecret
}