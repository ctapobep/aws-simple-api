const {Client} = require("pg");
const SecretsManager = require('@aws-sdk/client-secrets-manager').SecretsManager;
const secretManager = new SecretsManager({region: process.env.AWS_REGION});

const SCHEMA_NAME = 'helloworld';

exports.handler = async function(event) {
    const adminUserConnectionProps = await _getConnectionPropsFromSecret(_requiredEnv('DB_ADMIN_SECRET_ARN'));
    const appUserConnectionProps = await _getConnectionPropsFromSecret(_requiredEnv('DB_APP_SECRET_ARN'));

    const client = await _createConnection(adminUserConnectionProps);
    let resultMessage = 'The database was already initialized, do nothing;'
    if(!(await _dbAlreadySetUp(client, appUserConnectionProps))) {
        await _createDbAndUser(client, appUserConnectionProps);
        await _createAppSchema(client, adminUserConnectionProps, appUserConnectionProps);
        resultMessage = 'Created database, role and schema for the app.';
    }
    await client.end();

    return {
        statusCode: 200,
        headers: { "Content-Type": "text/plain" },
        body: resultMessage
    };
};

async function _getConnectionPropsFromSecret(dbSecretArn) {
    const secretString = (await secretManager.getSecretValue({SecretId: dbSecretArn})).SecretString;
    const {username, password, host, port, dbname} = JSON.parse(secretString);
    return {user: username, password, host, port, database: dbname};
}

async function _createAppSchema(client, adminUserConnectionProps, appUserConnectionProps) {
    const user = appUserConnectionProps.user;
    let query = `grant ${user} to ${adminUserConnectionProps.user}`;
    console.info('Granting admin the app role, so that it can create a schema for itself (otherwise we get "must be member of role ..."):\n', query);
    await client.query(query);

    query = `create schema ${SCHEMA_NAME} authorization ${user}`;
    console.info('Creating schema: \n' + query);
    await client.query(query);
}

/**
 *
 * @param {Client} client
 * @param {{database}} appUserConnectionProps
 * @return {Promise<boolean>}
 * @private
 */
async function _dbAlreadySetUp(client, appUserConnectionProps) {
    const query = `SELECT datname FROM pg_database where datname='${appUserConnectionProps.database}'`;
    console.info('Checking if the we have already set up the database:\n', query);
    const res = await client.query(query);
    return res.rows.length > 0;
}

async function _createDbAndUser(client, appUserConnectionProps) {
    const {database, user, password} = appUserConnectionProps;
    let query = `create database ${database}
          template template0
          encoding = 'utf8'
          -- How should the text fields be compared during sorting. 'C' means they are compared by their code points - which
          -- is the simplest, but it does mean that the text isn't always sorted alphabetically. Since we use text-based
          -- IDs, it's important to keep ID-generation consistent with the DB collation - ID that was generated later
          -- must also be considered larger from the DB perspective. See https://github.com/ctapobep/blog/issues/15
          --
          -- For text fields where alphabetical sorting is required, specify collation explicitly when creating the column.
          lc_collate = 'C'
          -- Determines what is a letter vs number/whitespace as well as how to lowercase/uppercase letters.
          -- On some installation it should be 'UTF-8'
          lc_ctype = 'en_US.utf8';
    `;
    console.info('Create a DB:\n' + query)
    await client.query(query);

    console.info('Creating DB role', user);
    await client.query(`create role ${user} with encrypted password '${password}' login;`);
}

async function _createConnection(connectionOpts) {
    const debugConnectionOpts = JSON.parse(JSON.stringify(connectionOpts));
    delete debugConnectionOpts.password;
    console.info('Connecting to ', debugConnectionOpts)

    const client = new Client(connectionOpts);
    await client.connect();
    return client;
}

function _requiredEnv(varName) {
    const val = process.env[varName];
    if(!val)
        throw new Error(varName + ' was not found in the environment variables!')
    return val;
}