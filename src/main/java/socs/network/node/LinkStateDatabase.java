// reference: http://cs.fit.edu/~ryan/java/programs/graph/Dijkstra-java.html
package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;

import java.util.HashMap;

public class LinkStateDatabase {

  private static final int INFINITE = Integer.MAX_VALUE;

  //linkID => LSAInstance
  HashMap<String, LSA> _store = new HashMap<String, LSA>();

  private RouterDescription rd = null;

  public LinkStateDatabase(RouterDescription routerDescription) {
    rd = routerDescription;
    LSA l = initLinkStateDatabase();
    _store.put(l.linkStateID, l);
  }

  /**
   * output the shortest path from this router to the destination with the given IP address
   */
  public String getShortestPath(String destinationIP) {
    //TODO: fill the implementation here

    String printString = "";
    // created weighted graph
    //System.out.println("size " + _store.size());
    WeightedGraph weightedGraph = new WeightedGraph(_store.size());
    int nodeNum = 0;
    for(LSA LSA: _store.values()) {
      weightedGraph.addNodes(nodeNum, LSA.linkStateID);
      nodeNum++;
    }
//    for(int i = 0; i < weightedGraph.nodes.length; i++) {
//    	System.out.printf("%d : %s\n", i, weightedGraph.nodes[i]);
//    }
    
//    for(int i = 0; i < _store.size(); i++) {
//    	for(int j = 0; j < _store.size(); j++) {
//    		System.out.printf("[%d][%d] = %d\n", i, j, weightedGraph.getWeight(i, j));
//    	}
//    }
    for(LSA LSA: _store.values()) {
        for(LinkDescription LD: LSA.links) {
//        	System.out.println(LSA.linkStateID);
//            System.out.println(weightedGraph.findNode(LSA.linkStateID));
//            System.out.println(LD.linkID);
//            System.out.println(weightedGraph.findNode(LD.linkID));
            weightedGraph.addEdges(weightedGraph.findNode(LSA.linkStateID), weightedGraph.findNode(LD.linkID) , (short)LD.tosMetrics);
          }
    }

    // Dijkstra's Algorithm
    int source = weightedGraph.findNode(rd.simulatedIPAddress);
    int destination = weightedGraph.findNode(destinationIP);
    int[] distance = new int[_store.size()];	// stores the shortest distance from source to each node
    int[] previousNodes = new int[_store.size()]; 	// previous node in the path
    int[] nodeVisited = new int[_store.size()];	// node visited or not


    // all nodes unvisited initially
    for(int i = 0; i < _store.size(); i++) {
      nodeVisited[i] = 0;
    }

    // distance from source to itself is 0 and every else node is infinity
    for(int i = 0; i < _store.size(); i++) {
      if(i != source) {
        distance[i] = INFINITE;
      }
      else if(i == source) {
        distance[source] = 0;
      }
    }

    for(int i = 0; i < _store.size(); i++) {
      int nextNode = closestNode(distance, nodeVisited);
      //System.out.println("next node numberi s: " + nextNode);
      if(nextNode == -1) {
    	  break;
      }
      else {
	      nodeVisited[nextNode] = 1;
	
	      // if cost of start->x->y is shorter than the cost of start->y, set x as y's parent
	      int[] neighbors = weightedGraph.neighbors(nextNode);
	      for(int j = 0; j < neighbors.length; j++) {
	        int vertex = neighbors[j];
	        int dist = distance[nextNode] + weightedGraph.getWeight(nextNode, vertex);
	
	        if(distance[vertex] > dist) {
	          distance[vertex] = dist;
	          previousNodes[vertex] = nextNode;
	        }
	      }
      }
    }

    // find shortest path by back-tracking the previous nodes
    int nextDestination = previousNodes[destination];
    if(destination != -1 && nextDestination != -1) {
      LinkDescription[] shortestPath = new LinkDescription[_store.size()];
      int count = _store.size() - 1;
      do {
        LinkDescription LD = new LinkDescription();
        LD.linkID = weightedGraph.nodes[destination];
        LD.tosMetrics = weightedGraph.getWeight(nextDestination, destination);
        shortestPath[count] = LD;
        destination = nextDestination;
        nextDestination = previousNodes[nextDestination];
        count--;

      }while(destination != source);

      printString = printString + rd.simulatedIPAddress;

      for(int i = count+1; i < _store.size(); i++) {
    	  printString = printString + " -> (" + shortestPath[i].tosMetrics + ") " + shortestPath[i].linkID ;
      }
    }

    return printString;
  }

  // choose the next node which has the least cost
  private static int closestNode(int[] distance, int[] nodeVisited) {
    int distance1 = INFINITE;
    int distance2 = -1;
    for(int i = 0 ; i < distance.length; i++) {
      if(nodeVisited[i] == 0 && distance[i] < distance1) {
        distance2 = i;
        distance1 = distance[i];
      }
    }
    return distance2;
  }

  //initialize the linkstate database by adding an entry about the router itself
  private LSA initLinkStateDatabase() {
    LSA lsa = new LSA();
    lsa.linkStateID = rd.simulatedIPAddress;
    lsa.lsaSeqNumber = Integer.MIN_VALUE;
    LinkDescription ld = new LinkDescription();
    ld.linkID = rd.simulatedIPAddress;
    ld.portNum = -1;
    ld.tosMetrics = 0;
    lsa.links.add(ld);
    return lsa;
  }

  public synchronized void addDesignatedLink(String sim_ip_address, LinkDescription ld) {
    LSA lsa = _store.get(sim_ip_address);
    if (lsa == null) {
      //under what circumstance would this even take.
      System.out.println("No LSA present at this ip address");
    } else {
      lsa.links.add(ld);
      System.out.println("Link between " + sim_ip_address + " and " + ld.linkID + " formed.");
      lsa.lsaSeqNumber++;
    }
  }

  public synchronized void removeDesignatedLink(String ip1, String ip2) {
    if(_store.get(ip1) != null) {
      LSA lsa = _store.get(ip1);
      for (int i = 0; i < lsa.links.size(); i++) {
        if (lsa.links.get(i).linkID.equals(ip2)) {
          lsa.links.remove(i);
          lsa.lsaSeqNumber++;
          System.out.print("Link between " + ip1 + " and " + ip2 + " has been removed.");
          return;
        }
      }
//      for (LinkDescription ld : lsa.links) {
//        if (ld.linkID.equals(ip2)) {
//          lsa.links.remove(ip2);
//        }
//      }
    } else {
      System.out.println("A link between " + ip2 + " does not exist.");
    }
  }
  
  public synchronized LinkDescription getLinkDescriptionFromLSD(String ip) {
	    LSA lsa = _store.get(this.rd.simulatedIPAddress);
	    for (LinkDescription ld : lsa.links) {
	      if(ld.linkID.equals(ip)) {
	        return ld;
	      }
	    }
	    return null;
	  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (LSA lsa: _store.values()) {
      sb.append(lsa.linkStateID).append("(" + lsa.lsaSeqNumber + ")").append(":\t");
      for (LinkDescription ld : lsa.links) {
        sb.append(ld.linkID).append(",").append(ld.portNum).append(",").
                append(ld.tosMetrics).append("\t");
      }
      sb.append("\n");
    }
    return sb.toString();
  }

}
