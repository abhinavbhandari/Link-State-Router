// reference: http://cs.fit.edu/~ryan/java/programs/graph/WeightedGraph-java.html

package socs.network.node;

public class WeightedGraph {
    short[][] edges;	//index stands for the two nodes of the links, the value of edge is linkWeight
    String[] nodes; // Store the simulatedIP for each vertex(router)
    int numNodes = 0;

    // construct weighted graph with given size
    public WeightedGraph(int size) {
        edges = new short[size][size];
        nodes = new String[size];
        numNodes = size;
    }

    // find the node with the same simulatedIP
    public int findNode(String simulatedIP) {
        for(int i = 0 ; i < numNodes; i++) {
            if(nodes[i].equals(simulatedIP)) {
                return i;
            }
        }
        return -1;
    }

    // add nodes
    public void addNodes(int node, String simulatedIP){
        nodes[node] = simulatedIP;
    }

    // add edges
    public void addEdges(int s, int t, short weight) {
        edges[s][t] = weight;
    }

    // returns weight of a specific edge
    public short getWeight(int s, int t) {
        return edges[s][t];
    }

    // returns all the adjacent neighbor nodes
    public int[] neighbors(int vertex) {
        int numNeighbors = 0;
        int[] neighbors;
        for (int i = 0; i < edges[vertex].length; i++) {
            if (edges[vertex][i] > 0) {
            	numNeighbors++;
            }
        }
        neighbors = new int[numNeighbors];
        int index = 0;
        for (int i = 0; i < edges[vertex].length; i++) {
            if (edges[vertex][i] > 0) {
                neighbors[index++] = i;
            }
        }
        return neighbors;
    }
}
