package socs.network.node;

import socs.network.message.LinkDescription;
import socs.network.message.SOSPFPacket;
import socs.network.util.Configuration;
import socs.network.message.LSA;

import java.io.*;
import java.net.*;
import java.util.Vector;


public class Router {

  protected LinkStateDatabase lsd;

  RouterDescription rd = new RouterDescription();

  //assuming that all routers are with 4 ports
  Link[] ports = new Link[4];
  int currentPortCount;
  private ServerSocket myServerSocket;
  private boolean serverOn = true;

  public Router(Configuration config) {
    //store local ip address in the following string
    String localIpAddress;
    //gets the local ip address from inetaddress
    try {
      localIpAddress= InetAddress.getLocalHost().getHostAddress();
    } catch (UnknownHostException e) {
      System.out.println("The following is an error with the IP Address: " + e.toString());
      //pad it with sample string
      localIpAddress = "";
    }

    currentPortCount = 0;
    rd.simulatedIPAddress = config.getString("socs.network.router.ip");
    short serverPort = (short) getRandomPort();
    rd.processPortNumber = serverPort;
    rd.processIPAddress = localIpAddress;
    rd.status = RouterStatus.INIT;

    lsd = new LinkStateDatabase(rd);
    try {
      myServerSocket = new ServerSocket(serverPort);
      ServerThread serverThread = new ServerThread(myServerSocket, this);
      serverThread.start();
    } catch (IOException e) {
      System.out.println("Could not create server socket on port 11111. Quitting.");
      System.exit(-1);
    }

  }

  private int getRandomPort() {
    return 1000 + (int) (Math.random() * 6000);
  }

  /**
   * output the shortest path to the given destination ip
   * <p/>
   * format: source ip address  -> ip address -> ... -> destination ip
   *
   * @param destinationIP the ip adderss of the destination simulated router
   */
  private void processDetect(String destinationIP) {
      if(destinationIP.equals(rd.simulatedIPAddress)){
          System.out.println("This is your own IP, distance is (0)");
      }
      else {
          System.out.println(lsd.getShortestPath(destinationIP));
      }
  }

  /**
   * disconnect with the router identified by the given destination ip address
   * Notice: this command should trigger the synchronization of database
   *
   * @param portNumber the port number which the link attaches at
   */
  private void processDisconnect(short portNumber) {

  }

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * additionally, weight is the cost to transmitting data through the link
   * <p/>
   * NOTE: this command should not trigger link database synchronization
   */
  private void processAttach(String processIP, short processPort,
                             String simulatedIP, short weight) {

    if (currentPortCount < 4) {
      RouterDescription rd2 = new RouterDescription();
      rd2.processIPAddress = processIP;
      rd2.processPortNumber = processPort;
      rd2.simulatedIPAddress = simulatedIP;
      rd2.status = RouterStatus.INIT;

      //check if passed port is itself
      if (rd2.simulatedIPAddress.equals(rd.simulatedIPAddress)) {
          System.out.println("Cannot attach router to itself");
      }

      //check if already in ports
      for (int i = 0; i < 4; i++) {
          //check if already in ports
          if(ports[i] != null && ports[i].router2.simulatedIPAddress.equals(rd2.simulatedIPAddress)) {
              System.out.println("This router is already attached");
              return;
          }
      }

      for (int i = 0; i < 4; i++) {
          if(ports[i] == null) {
              ports[i] = new Link(rd, rd2, weight);
              //possibly add it router description
              ports[i].addWeight(weight);
              currentPortCount++;
              return;
          }
      }
    } else {
      System.out.println("All ports full");
    }

  }

  /**
   * broadcast Hello to neighbors
   */
  private void processStart() {
      for(Link link : ports){
          if (link == null) {
              continue;
          }
          RouterDescription remoteServerRD = link.router2;
          try {
              Socket clientSocket = new Socket(remoteServerRD.processIPAddress, remoteServerRD.processPortNumber);
              SOSPFPacket helloPacket = makePacket(rd, remoteServerRD, (short)0, false);
              helloPacket.weight = link.getWeight();
              ObjectOutputStream objectOutputStream = new ObjectOutputStream(clientSocket.getOutputStream());
              objectOutputStream.writeObject(helloPacket);
              ObjectInputStream objectInputStream = new ObjectInputStream(clientSocket.getInputStream());
              try {
                  SOSPFPacket serverPacket = (SOSPFPacket) objectInputStream.readObject();
                  //check if already connected
                  if (remoteServerRD.status == RouterStatus.TWO_WAY) {
                      if (serverPacket.sospfType == 0) {
                          System.out.println("Received HELLO from " + serverPacket.neighborID);
                          updateRouterStatus(remoteServerRD);
                          continue;
                      }
                  }
                  if(serverPacket.dstIP.equals(rd.simulatedIPAddress)) {
                      if(serverPacket.sospfType == 0) {
                          System.out.println("Received HELLO from " + serverPacket.neighborID);
                          link.router2.status = RouterStatus.TWO_WAY;
                          updateRouterStatus(link.router2);
                      }

                      ObjectOutputStream secondOutput = new ObjectOutputStream(clientSocket.getOutputStream());
                      secondOutput.writeObject(helloPacket);
                  }
                  LinkDescription ld = makeLinkDescription(link);
                  lsd.addDesignatedLink(rd.simulatedIPAddress, ld);

              } catch (ClassNotFoundException e) {
                  e.printStackTrace();
              }
              clientSocket.close();
          } catch (IOException e) {
              e.printStackTrace();
          }
      }

      lsaUpdate();
  }

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * additionally, weight is the cost to transmitting data through the link
   * <p/>
   * This command does trigger the link database synchronization
   */
  private void lsaUpdate() {
      for (Link link : ports) {
          if (link != null && link.router2.status == RouterStatus.TWO_WAY) {
              //Socket client = new Socket(link.router2.processIPAddress, );
              RouterDescription remoteServer = link.router2;
              try {
                  Socket clientSocket = new Socket(remoteServer.processIPAddress, remoteServer.processPortNumber);
                  SOSPFPacket lsaPacket = makePacket(rd, remoteServer, (short)1, true);
                  lsaPacket.weight = link.getWeight();
                  ObjectOutputStream objectOutputStream = new ObjectOutputStream(clientSocket.getOutputStream());
                  System.out.println("LSAUPDATE: Sending an LSP to " + lsaPacket.dstIP);
                  objectOutputStream.writeObject(lsaPacket);
                  clientSocket.close();


              } catch (IOException e) {
                  e.printStackTrace();
              }
          }
      }
  }

  public LinkDescription makeLinkDescription(Link link) {
      RouterDescription rd = link.router2;
      LinkDescription linkDescription = new LinkDescription();
      linkDescription.linkID = rd.simulatedIPAddress;
      linkDescription.portNum = rd.processPortNumber;
      linkDescription.tosMetrics = link.getWeight();
      return linkDescription;
  }

  private void processConnect(String processIP, short processPort,
                              String simulatedIP, short weight) {

  }

  /**
   * output the neighbors of the routers
   */
  private void processNeighbors() {
      int neighbordCount = 1;
      for (Link neighbor : ports) {
          // && neighbor.router2.status == RouterStatus.TWO_WAY
          if (neighbor != null && neighbor.router2.status == RouterStatus.TWO_WAY) {
              System.out.println("IP address of neighbor " + neighbordCount + " " + neighbor.router2.simulatedIPAddress);
              neighbordCount++;
          }
      }

  }

  /**
   * disconnect with all neighbors and quit the program
   */
  private void processQuit() {

  }

  public SOSPFPacket makePacket(RouterDescription rd1, RouterDescription rd2, short messageVal, boolean addLSA) {
      SOSPFPacket packet = new SOSPFPacket();

      packet.srcProcessIP = rd1.processIPAddress;
      packet.srcProcessPort = rd1.processPortNumber;
      packet.srcIP = rd1.simulatedIPAddress;
      packet.dstIP = rd2.simulatedIPAddress;
      packet.routerID = rd1.simulatedIPAddress;
      packet.neighborID = rd1.simulatedIPAddress;
      packet.sospfType = messageVal;
      packet.lsaArray = new Vector<LSA>();
      if (addLSA) {
          for (LSA l : lsd._store.values()) {
              //this is synchronized
              if(l != null) {
                  packet.lsaArray.addElement(l);
              }
          }
      }

      return packet;
  }

  private void printLinkDescriptions() {
      LSA lsa = lsd._store.get(rd.simulatedIPAddress);
      for (LinkDescription ld : lsa.links) {
          System.out.println(ld.toString());
      }
      System.out.println();
  }

  public void updateRouterStatus(RouterDescription rd) {
      System.out.println("Set " + rd.simulatedIPAddress + " state to " + rd.status);

  }

  public void terminal() {
    try {
      InputStreamReader isReader = new InputStreamReader(System.in);
      BufferedReader br = new BufferedReader(isReader);
      System.out.print(">> ");
      String command = br.readLine();
      while (true) {
        if (command.startsWith("detect ")) {
          String[] cmdLine = command.split(" ");
          processDetect(cmdLine[1]);
        } else if (command.startsWith("disconnect ")) {
          String[] cmdLine = command.split(" ");
          processDisconnect(Short.parseShort(cmdLine[1]));
        } else if (command.startsWith("quit")) {
          processQuit();
        } else if (command.startsWith("attach ")) {
          String[] cmdLine = command.split(" ");
          processAttach(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3], Short.parseShort(cmdLine[4]));
        } else if (command.equals("start")) {
          processStart();
        } else if (command.equals("connect ")) {
          String[] cmdLine = command.split(" ");
          processConnect(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3], Short.parseShort(cmdLine[4]));
        } else if (command.equals("neighbors")) {
          //output neighbors
          processNeighbors();
        } else if (command.equals("exit")) {
            System.out.println("Closing reader...");
            break;
        } else if (command.equals("linkdescriptions")) {
            printLinkDescriptions();
        }
        //invalid command
        System.out.print(">> ");
        command = br.readLine();
        //break;

        //System.out.print(">> ");
        //command = br.readLine();
      }
      isReader.close();
      br.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
