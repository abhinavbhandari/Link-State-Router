package socs.network.node;
import com.typesafe.config.ConfigException;
import socs.network.message.LSA;
import socs.network.message.LinkDescription;
import socs.network.message.SOSPFPacket;

import java.io.*;
import java.io.ObjectInputStream;
import java.net.*;
public class ServerThread extends Thread {

    boolean serverOn = true;
    ServerSocket myServerSocket;
    Router router;
    public ServerThread(ServerSocket serverSocket, Router router) {
        this.myServerSocket = serverSocket;
        this.router = router;
    }

    @Override
    public void run() {
        super.run();
        while(serverOn) {
            try {
                System.out.println("Waiting for client on " + myServerSocket.getLocalPort());
                Socket clientSocket = myServerSocket.accept();
                boolean firstMessageReceivedPreviously = false;
                short previouslyReceivedLoc = (short) -1;
                //System.out.println("Connection Made with " + clientSocket.getRemoteSocketAddress());

                //Get client input stream and check if it's hello
                ObjectInputStream objectInputStream = new ObjectInputStream(clientSocket.getInputStream());
                SOSPFPacket clientPacket = (SOSPFPacket) objectInputStream.readObject();

                //System.out.println("sofstype: " + clientPacket.sospfType + " from " + clientPacket.srcIP);

                //change the client's status type to INIT
                if (clientPacket.sospfType == 0) {
                    //check if client packet already is in this
                    for (int i = 0; i < 4; i++) {
                        if (router.ports[i] != null && clientPacket.neighborID.equals(router.ports[i].router2.simulatedIPAddress)) {
                            //TODO Figure out what to do in the case of message already being received previously
                            //router.ports[i].router2.status = RouterStatus.INIT;
                            //System.out.println("Set " + clientPacket.neighborID + " state to " + router.ports[i].router2.status);
                            firstMessageReceivedPreviously = true;
                            previouslyReceivedLoc = (short) i;
                            break;
                        }
                    }
                    if (firstMessageReceivedPreviously) {
                        System.out.println("Received HELLO from " + router.ports[previouslyReceivedLoc].router2.simulatedIPAddress);
                        router.updateRouterStatus(router.ports[previouslyReceivedLoc].router2);
                        ObjectOutputStream objectOutputStream = new ObjectOutputStream(clientSocket.getOutputStream());
                        SOSPFPacket serverPacket = router.makePacket(router.rd, router.ports[previouslyReceivedLoc].router2, (short) 0, true);
                        //send server packet to the client
                        objectOutputStream.writeObject(serverPacket);
                        clientSocket.close();
                        continue;
                    }

                    //Create a new remote routeDescription

                    System.out.println("Received HELLO from " + clientPacket.neighborID);
                    RouterDescription newRemoteRD = makeRouterDescription(clientPacket, RouterStatus.INIT);
                    router.updateRouterStatus(newRemoteRD);

                    //First Hello message that the server is going to return to the client
                    ObjectOutputStream objectOutputStream = new ObjectOutputStream(clientSocket.getOutputStream());
                    SOSPFPacket serverPacket = router.makePacket(router.rd, newRemoteRD, (short) 0, false);
                    //send server packet to the client
                    objectOutputStream.writeObject(serverPacket);

                    //check for second hello message
                    ObjectInputStream secondInputStream = new ObjectInputStream(clientSocket.getInputStream());
                    SOSPFPacket clientPacket2 = (SOSPFPacket) secondInputStream.readObject();
                    if (clientPacket2.sospfType == 0) {
                        System.out.println("Received HELLO from " + clientPacket.neighborID);
                        newRemoteRD.status = RouterStatus.TWO_WAY;
                        router.updateRouterStatus(newRemoteRD);

                        //add router to ports
                        for (int i = 0; i < 4; i++) {
                            if(router.ports[i] == null) {
                                router.ports[i] = new Link(router.rd, newRemoteRD, clientPacket2.weight );
                                router.currentPortCount++;
                                break;
                            }
                        }
                    }
                    clientSocket.close();
                } else if(clientPacket.sospfType == 1) {
                    //something more
                    boolean sendLSP = false;
                    boolean newNeighbor = false;
                    int nid;
                    LSA neighLSA;

                    for (LSA lsa : clientPacket.lsaArray) {
                        //LSA from the map of linkstatedatabase
                        if (router.lsd._store.get(lsa.linkStateID) != null) {
                            LSA mapL = router.lsd._store.get(lsa.linkStateID);
                            if (mapL.lsaSeqNumber < lsa.lsaSeqNumber) {
                                //System.out.println("Improvement in sequence number");
                                sendLSP = true;
                                //check if neighbor or not
                                router.lsd._store.remove(mapL.linkStateID);
                                router.lsd._store.put(lsa.linkStateID, lsa);
                                int check = isNeighbor(mapL, true);
                                if (check >= 0) {
                                    if (check < 10) {
                                        LinkDescription ld = router.lsd.getLinkDescriptionFromLSD(lsa.linkStateID);
                                        if (ld == null) {
                                            ld = router.makeLinkDescription(router.ports[check]);
                                            this.router.lsd.addDesignatedLink(router.rd.simulatedIPAddress, ld);
                                            newNeighbor = true;
                                        }
//                                        neighLSA = lsa;
//                                        nid = check;
                                        //newNeighbor = true;
                                    } else {
                                        this.router.lsd.removeDesignatedLink(this.router.rd.simulatedIPAddress, lsa.linkStateID);
                                    }
                                }
                            }
                        } else {
                            sendLSP = true;
                            router.lsd._store.put(lsa.linkStateID, lsa);
                            int check = isNeighbor(lsa, false);
                            if (check >= 0) {
                                if (check < 10) {
                                    //putting a check of if a link description exists or not.
                                    if(router.lsd.getLinkDescriptionFromLSD(router.ports[check].router2.simulatedIPAddress) != null) {
                                        continue;
                                    }
                                    //System.out.println("inside the neighbor");
                                    neighLSA = lsa;
                                    nid = check;
                                    newNeighbor = true;
                                    LinkDescription newld = new LinkDescription();
                                    newld.linkID = this.router.ports[nid].router2.simulatedIPAddress;
                                    newld.tosMetrics = this.router.ports[nid].getWeight();
                                    newld.portNum = nid;
                                    this.router.lsd.addDesignatedLink(router.rd.simulatedIPAddress, newld);
                                } else {
                                    this.router.lsd.removeDesignatedLink(this.router.rd.simulatedIPAddress, lsa.linkStateID);
                                }
                            }
                        }
                    }

                    if(sendLSP) {
                        for (int i = 0; i < this.router.ports.length; i++) {
                            Link link = this.router.ports[i];
                            if (link != null) {
                                RouterDescription rd1 = link.router1;
                                RouterDescription rd2 = link.router2;
                                if (rd2.status == RouterStatus.TWO_WAY) {

                                    if (newNeighbor == false && rd2.simulatedIPAddress.equals(clientPacket.srcIP)) {
                                        continue;
                                    }
                                    Socket lsaClientSocket = new Socket(rd2.processIPAddress, rd2.processPortNumber);
                                    ObjectOutputStream objectOutputStream = new ObjectOutputStream(lsaClientSocket.getOutputStream());
                                    SOSPFPacket serverLSP = this.router.makePacket(rd1, rd2, (short) 1, true);
                                    //send server packet to the client
                                    System.out.println("LSAUPDATE: Sending an LSP to " + serverLSP.dstIP);
                                    objectOutputStream.writeObject(serverLSP);
                                    lsaClientSocket.close();
                                }
                            }
                        }
                        sendLSP = false;
                    }
                    //System.out.println("Size of lsd " + router.lsd._store.size());
                    //System.out.println();
                    clientSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

    }

    private int isNeighbor(LSA lsa, boolean ldExist) {
        RouterDescription rd2;
        for (int i = 0; i < this.router.ports.length; i++) {
            if (this.router.ports[i] != null) {
                rd2 = this.router.ports[i].router2;
                if (rd2.simulatedIPAddress.equals(lsa.linkStateID) && rd2.status == RouterStatus.TWO_WAY) {
//                    if (ldExist) {
//                        for (LinkDescription ld : lsa.links) {
//                            if (ld.linkID.equals(this.router.rd.simulatedIPAddress)) {
//                                return i;
//                            }
//                        }
//                    } else {
//                        return i;
//                    }
//                    //remove the link to this router
//                    this.router.ports[i] = null;
//                    return -1;
                    return i;
                }
            }
        }
        return 10;
    }

    private RouterDescription makeRouterDescription(SOSPFPacket packet, RouterStatus rs) {
        RouterDescription routerDescription = new RouterDescription();
        routerDescription.processIPAddress = packet.srcProcessIP;
        routerDescription.processPortNumber = packet.srcProcessPort;
        routerDescription.simulatedIPAddress = packet.neighborID;
        routerDescription.status = rs;
        return routerDescription;
    }

    public void shutDown() {
        this.serverOn = false;
        try {
            myServerSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
