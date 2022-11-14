package sandbox.aws.simpleapi;

import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Stack;

import static sandbox.aws.simpleapi.Retry.retry;

public class Rds {
    private final RdsClient rds;
    private final Stack<RdsResponse> responses = new Stack<>();

    public Rds(RdsClient rds) {
        this.rds = rds;
    }

    public DBInstance createPgInstance(String az, String secGroup, Collection<Subnet> subnets,
                                       String dbName, String masterUser, String masterPassword) {
        List<String> subnetIds = new ArrayList<>(subnets.size());
        for (Subnet subnet : subnets)
            subnetIds.add(subnet.id);
        String subnetGroup = "DB Subnets";
        if(!isDbSubnetGroupExists(subnetGroup))
            resp(rds.createDBSubnetGroup(CreateDbSubnetGroupRequest.builder()
                    .dbSubnetGroupName(subnetGroup)
                    .subnetIds(subnetIds)
                    .dbSubnetGroupDescription("Databases are hosted within these subnets")
                    .build()));
        return resp(this.rds.createDBInstance(CreateDbInstanceRequest.builder()
                .availabilityZone(az)
                .allocatedStorage(20)
                .dbSubnetGroupName(subnetGroup)
                .vpcSecurityGroupIds(secGroup)
                .engine("postgres").engineVersion("14.4")
                .dbInstanceClass("db.t3.micro")
                .dbInstanceIdentifier(dbName).masterUsername(masterUser).masterUserPassword(masterPassword)
                .build())).dbInstance();
    }
    public void startDbInstance(DBInstance dbInstance) {
        waitForStatus(dbInstance, "available");
        rds.startDBInstance(StartDbInstanceRequest.builder()
                .dbInstanceIdentifier(dbInstance.dbInstanceIdentifier()).build());
    }
    public void deleteCreatedResources() {
        for (RdsResponse resp : responses) {
            if(resp instanceof CreateDbInstanceResponse r) {
                DBInstance dbInstance = r.dbInstance();
                String id = dbInstance.dbInstanceIdentifier();
                rds.stopDBInstance(StopDbInstanceRequest.builder().dbInstanceIdentifier(id).build());
                rds.deleteDBInstance(DeleteDbInstanceRequest.builder()
                        .dbInstanceIdentifier(id)
                        .skipFinalSnapshot(true)
                        .deleteAutomatedBackups(true).build());
                waitForDeletion(dbInstance);
            }
            if(resp instanceof CreateDbSubnetGroupResponse r)
                rds.deleteDBSubnetGroup(DeleteDbSubnetGroupRequest.builder()
                        .dbSubnetGroupName(r.dbSubnetGroup().dbSubnetGroupName()).build());
        }
    }
    private boolean isDbSubnetGroupExists(String subnetGroup) {
        return rds.describeDBSubnetGroups()
                .dbSubnetGroups().stream()
                .anyMatch((sg) -> sg.dbSubnetGroupName().equals(subnetGroup));
    }
    private void waitForDeletion(DBInstance dbInstance) {
        retry("Waiting for deletion of " + dbInstance, (c) -> {
            if(describeDbInstance(dbInstance.dbiResourceId()) != null)
                throw new IllegalStateException("Still exists");
            return null;
        });
    }
    private void waitForStatus(DBInstance dbInstance, String waitFor) {
        retry(600_000, "Working with " + dbInstance, (c) -> {
            if(c.getRetryCount() > 0)
                System.out.println("Working with DB, still not "+waitFor+". Waiting #" + c.getRetryCount());
            String status = describeDbInstance(dbInstance.dbInstanceIdentifier()).dbInstanceStatus();
            if (!waitFor.equalsIgnoreCase(status))
                throw new IllegalStateException("Still pending");
            return status;
        });
    }
    private DBInstance describeDbInstance(String dbInstanceId) {
        List<DBInstance> instances = rds.describeDBInstances(DescribeDbInstancesRequest.builder()
                .dbInstanceIdentifier(dbInstanceId).build()).dbInstances();
        if(instances.isEmpty())
            return null;
        return instances.get(0);
    }
    private <T extends RdsResponse> T resp(T resp) {
        System.out.println(resp);
        this.responses.add(resp);
        return resp;
    }
}
