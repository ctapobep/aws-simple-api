package sandbox.aws.simpleapi;

import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.CreateTaskSetRequest;

public class Ecs {
    private final EcsClient ecsClient;

    public Ecs(EcsClient ecsClient) {
        this.ecsClient = ecsClient;
    }

    public void createTask() {
//        ecsClient.createTaskSet(CreateTaskSetRequest.builder().taskDefinition()
//                        )
//                .build())
    }
}
