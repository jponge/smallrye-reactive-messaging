package broadcast;

import io.smallrye.reactive.messaging.annotations.Broadcast;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

public class BroadcastWithCountExamples {

    // tag::chain[]
    @Incoming("in")
    @Outgoing("out")
    @Broadcast(2)
    public int increment(int i) {
        return i + 1;
    }

    @Incoming("out")
    public void consume1(int i) {
        //...
    }

    @Incoming("out")
    public void consume2(int i) {
        //...
    }
    // end::chain[]

}
