# Split brain resolver with Down All strategy

The split brain resolver is added and configured with a `Down All` strategy.

# Instructions

- Read the [SBR Down All strategy documentation](https://developer.lightbend.com/docs/akka-commercial-addons/current/split-brain-resolver.html#down-all)
- Also read the 
- Build & transfer the application
- Start up the application on all nodes and wait for the cluster to converge
- Partition the network in two by removing the white cable between the two
  switches
  - Observe what happens and try to explain it

# LED Legend

- LEDs 1 to 5 show the status of each node as seen by a node
    - Green:      Node is Up
    - Red:        Node is Down
    - Cyan:       Node in Leaving
    - Magenta:    Node is Exiting
    - White:      Node is Unreachable
    - Dark Green: Node is WeaklyUp - The LED is blinking

- LED number 6: Cyan when the node has a leader role
- LED number 7: Dark Blue when the node is running the Cluster Singleton actor
- LED number 8: Cluster liveliness indicator: when it blinks, we know
                that the cluster node software is actually running