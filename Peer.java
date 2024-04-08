/** Peer.java - A peer-to-peer file sharing program
 *
 *  @version CS 391 - Spring 2024 - A4
 *
 *  @author Ethan Lenz
 *
 *  @author Evan Olson
 * 
 *  @bug The initial join of a new peer [does/does not] work fully. [pick one]
 *
 *  @bug The Status command [does/does not] work fully. [pick one]
 *
 *  @bug The Find command [does/does not] work fully. [pick one]
 *
 *  @bug The Get command [does/does not] work fully. [pick one]
 * 
 *  @bug The Quit command [does/does not] work fully. [pick one]
 * 
 **/

import java.io.*;
import java.net.*;
import java.util.*;

class Peer
{
    String name,    // symbolic name of the peer, e.g., "P1" or "P2"
        ip,         // IP address in dotted decimal notation, e.g., "127.0.0.1"
        filesPath;  // path to local file repository, e.g., "dir1/dir2/dir3"
    int lPort,      // lookup port number (permanent UDP port)
        ftPort;     // file transfer port number (permanent TCP port)
    List<Neighbor> neighbors;      // current neighbor peers of this peer
    LookupThread  lThread;         // thread listening via the lookup socket
    FileTransferThread  ftThread;  // thread listening via file transfer socket
    int seqNumber;                 // identifier for next Find request (used to
                                   // control the flooding by avoiding loops)
    Scanner scanner;               // used for keyboard input
    HashSet<String> findRequests;  // record of all lookup requests seen so far

    /* Instantiate a new peer (including setting a value of 1 for its initial
       sequence number), launch its lookup and file transfer threads,
       (and start the GUI thread, which is already implemented for you)
     */
    Peer(String name, String ip, int lPort, String filesPath, 
         String nIP, int nPort)
    {
        this.name = name;
        this.ip = ip;
        this.lPort = lPort;
        this.filesPath = filesPath;
        seqNumber = 1;
        neighbors = new ArrayList<>();
        findRequests = new HashSet<>();
        GUI gUI = new GUI();
        gUI.createAndShowGUI(name);
        try {
            scanner = new Scanner(System.in);
            lThread = new LookupThread();
            lThread.start();
            ftThread = new FileTransferThread();
            ftThread.start();
            if (nIP != null) {
            	neighbors.add(new Neighbor(nIP, nPort));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }// constructor

    /* display the commands available to the user
       Do NOT modify this method.
     */
    static void displayMenu()
    {
        System.out.println("\nYour options:");
        System.out.println("    1. [S]tatus");
        System.out.println("    2. [F]ind <filename>");
        System.out.println("    3. [G]et <filename> <peer IP> <peer port>");
        System.out.println("    4. [Q]uit");
        System.out.print("Your choice: ");
    }// displayMenu method

    /* input the next command chosen by the user
     */
    int getChoice()
    {
    	String choice = scanner.next();
        if (choice.equalsIgnoreCase("s") || choice.equals("1")) {
        	return 1;
        }
        else if (choice.equalsIgnoreCase("f") || choice.equals("2")) {
        	return 2;
        }
        else if (choice.equalsIgnoreCase("g") || choice.equals("3")) {
        	return 3;
        }
        else if (choice.equalsIgnoreCase("q") || choice.equals("4")) {
        	return 4;
        }
        return -1;
    }// getChoice method
        
    /* this is the implementation of the peer's main thread, which
       continuously displays the available commands, inputs the user's
       choice, and executes the selected command, until the latter
       is "Quit"
     */
    void run() {
        while (true) {
        	displayMenu();
        	int choice = getChoice();
        	System.out.println("\n==================");
        	
        	if (choice == 1) {
        		processStatusRequest();
        	} 
        	else if (choice == 2) {
        		processFindRequest();
        	}
        	else if (choice == 3) {
        		processGetRequest();
        	}
        	else if (choice == 4) {
        		processQuitRequest();
        		break;
        	}
        	else {
        		System.out.println("Invalid choice.");
        		System.out.println("==================");
        	}
        }
    }// run method

    /* execute the Quit command, that is, send a "leave" message to all of the
       peer's neighbors, then terminate the lookup thread
     */
    void processQuitRequest() {
        for (Neighbor neighbor : neighbors) {
            try (DatagramSocket socket = new DatagramSocket()) {
                String leaveMessage = "leave";
                byte[] buf = leaveMessage.getBytes();
                InetAddress address = InetAddress.getByName(neighbor.ip);
                DatagramPacket packet = new DatagramPacket(buf, buf.length, address, neighbor.port);
                socket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        lThread.terminate();
    }// processQuitRequest method

    /* execute the Status command, that is, read and display the list
       of files currently stored in the local directory of shared
       files, then print the list of neighbors. The EXACT format of
       the output of this method (including indentation, line
       separators, etc.) is specified via examples in the assignment
       handout.
     */
    void processStatusRequest() {
    	File directory = new File(filesPath);
    	File[] files = directory.listFiles();

    	if (files != null)
    	{
    		System.out.print("Local files:");
        	for (File file : files) {
        	    if (file.isFile()) {
        	        System.out.print("\n    " + file.getName());
        	    }
        	}
    	}
    	else {
    		System.out.print("Error finding files");
    	}
    	
    	System.out.println();
    	printNeighbors();
    	System.out.println("==================");
    }// processStatusRequest method

    /* execute the Find command, that is, prompt the user for the file
       name, then look it up in the local directory of shared
       files. If it is there, inform the user. Otherwise, send a
       lookup message to all of the peer's neighbors. The EXACT format
       of the output of this method (including the prompt and
       notification), as well as the format of the 'lookup' messages
       are specified via examples in the assignment handout. Do not forget
       to handle the Find-request ID properly.
     */
    void processFindRequest() {
        scanner = new Scanner(System.in);
        System.out.print("Name of file to find: ");
        String fileName = scanner.nextLine().trim();
    
        File directory = new File(filesPath);
        File[] files = directory.listFiles();
        boolean foundLocally = false;
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().equals(fileName)) {
                    foundLocally = true;
                    break;
                }
            }
        }
        if (foundLocally) {
            System.out.println("This file exists locally in " + filesPath);
        } else {
            seqNumber++;
            String findRequest = "lookup " + seqNumber + " " + fileName;
            findRequests.add(seqNumber + fileName);
            for (Neighbor neighbor : neighbors) {
                try (DatagramSocket socket = new DatagramSocket()) {
                    byte[] buf = findRequest.getBytes();
                    InetAddress address = InetAddress.getByName(neighbor.ip);
                    DatagramPacket packet = new DatagramPacket(buf, buf.length, address, neighbor.port);
                    socket.send(packet);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("==================");
    }// processFindRequest method

    /* execute the Get command, that is, prompt the user for the file
       name and address and port number of the selected peer with the
       needed file. Send a "get" message to that peer and wait for its
       response. If the file is not available at that peer, inform the
       user. Otherwise, extract the file contents from the response,
       output the contents on the user's terminal and save this file
       (under its original name) in the local directory of shared
       files.  The EXACT format of this method's output (including the
       prompt and notification), as well as the format of the "get"
       messages are specified via examples in the assignment
       handout.
     */
    void processGetRequest() {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("Enter the file name: ");
            String fileName = scanner.nextLine().trim();
            System.out.print("Address of source peer: ");
            String peerIP = scanner.nextLine().trim();
            System.out.print("Port of source peer: ");
            int peerPort = Integer.parseInt(scanner.nextLine().trim());
    
            try (Socket socket = new Socket(peerIP, peerPort);
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                String getRequest = "get " + fileName;
                out.writeUTF(getRequest);
            } catch (IOException e) {
                System.out.println("Error occurred while sending the 'get' message: " + e.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }// processGetRequest method

    /* create a text file in the local directory of shared files whose
       name and contents are given as arguments.
     */
    void writeFile(String fileName, String contents) {
    	PrintWriter out = null;
        try {
        	File file = new File(fileName, filesPath);
        	if (file.createNewFile()) {
        		out = new PrintWriter(file);
            	out.write(contents);
        	}
        }
        catch (IOException e) {
        	System.out.println(e);
        }
        finally {
        	if (out != null) {
        		out.close();
        	}
        }
    }// writeFile method

    /* Send to the user's terminal the list of the peer's
       neighbors. The EXACT format of this method's output is specified by
       example in the assignment handout.
     */
    void printNeighbors() {
    	System.out.print("Neighbors: ");
        for (Neighbor n : neighbors) {
        	System.out.print("\n    " + n.toString());
        }
        System.out.println();
    }// printNeighbors method

    /* Do NOT modify this inner class
     */
    class Neighbor
    {
        String ip;
        int port;

        Neighbor(String ip, int port)
        {
            this.ip = ip;
            this.port = port;
        }// constructor

        public boolean equals(Object o)
        {
            if (o == this) { return true;  }
            if (!(o instanceof Neighbor)) { return false;  }        
            Neighbor n = (Neighbor) o;         
            return n.ip.equals(ip) && n.port == port;
        }// equals method

        public String toString()
        {
            return ip + ":" + port;
        }// toString method
    }// Neighbor class

    class LookupThread extends Thread
    {   
        DatagramSocket socket = null;           // UDP server socket
        private volatile boolean stop = false;  // flag used to stop the thread

        /* Stop the lookup thread by closing its server socket. This
           works (in a not-so-pretty way) because this thread's run method is
           constantly listening on that socket.
           Do NOT modify this method.
         */
        public void terminate()
        {
            stop = true;
            socket.close();
        }// terminate method

        /* This is the implementation of the thread that listens on
           the UDP lookup socket. First (at startup), if the peer has
           exactly one neighbor, send a "join" message to this
           neighbor. Otherwise, skip this step. Second, continuously
           wait for an incoming datagram (i.e., a request), display
           its contents in the GUI's Lookup panel, and process the
           request using the helper method below.
        */
        public void run() {
            try {
                socket = new DatagramSocket(lPort);
                if (neighbors.size() == 1) {               	
                	process("join " + neighbors.get(0).ip + " " + neighbors.get(0).port);
                }
                while (!stop) {
                    byte[] buf = new byte[256];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    GUI.displayLU(new String(packet.getData()));
                    String request = new String(packet.getData(), 0, packet.getLength());
                    process(request);
                }
            } catch (IOException e) {
                System.out.println("Error in LookupThread: " + e.getMessage());
            } finally {
                if (socket != null) {
                    socket.close();
                }
            }
        }// run method

        /* This helper method processes the given request, which was
           received by the Lookup socket. Based on the first field of
           the request (i.e., the "join", "leave", "lookup", or "file"
           keyword), perform the appropriate action. All actions are
           quite short, except for the "lookup" request, which has its
           own helper method below.
         */
        void process(String request) {
            StringTokenizer tokenizer = new StringTokenizer(request);
            String keyword = tokenizer.nextToken();
            switch (keyword) {
                case "join":
                    // Implement join message processing here
                    break;
                case "leave":
                    // Implement leave message processing here
                    break;
                case "lookup":
                    processLookup(tokenizer);
                    break;
                case "file":
                    // Implement file message processing here
                    break;
                default:
                    System.out.println("Unknown message received: " + request);
                    break;
            }
        }// process method

        /* This helper method processes a "lookup" request received
           by the Lookup socket. This request is represented by the
           given tokenizer, which contains the whole request line,
           minus the "lookup" keyword (which was removed from the
           beginning of the request) by the caller of this method.
           Here is the algorithm to process such requests:
           If the peer already received this request in the past (see request
           ID), ignore the request. 
           Otherwise, check if the requested file is stored locally (in the 
           peer's directory of shared files):
             + If so, send a "file" message to the source peer of the request 
               and, if necessary, add this source peer to the list 
               of neighbors of this peer.
             + If not, send a "lookup" message to all neighbors of this peer,
               except the peer that sent this request (that is, the "from" peer
               as opposed to the "source" peer of the request).
         */
        void processLookup(StringTokenizer line) {
            int requestID = Integer.parseInt(line.nextToken());
            String fileName = line.nextToken();
            String sourceIP = null;
            int sourcePort = 0;
            if (line.hasMoreTokens()) {
                sourceIP = line.nextToken();
                if (line.hasMoreTokens()) {
                    sourcePort = Integer.parseInt(line.nextToken());
                }
            }
        
            if (!findRequests.contains(requestID + fileName)) {
                File directory = new File(filesPath);
                File[] files = directory.listFiles();
                boolean foundLocally = false;
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && file.getName().equals(fileName)) {
                            foundLocally = true;
                            break;
                        }
                    }
                }
                if (foundLocally) {
                    String fileMessage = "file " + requestID + " " + name + " " + ip + " " + ftPort + " " + fileName;
                    for (Neighbor neighbor : neighbors) {
                        try (DatagramSocket socket = new DatagramSocket()) {
                            byte[] buf = fileMessage.getBytes();
                            InetAddress address = InetAddress.getByName(neighbor.ip);
                            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, neighbor.port);
                            socket.send(packet);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    String lookupMessage = "lookup " + requestID + " " + fileName;
                    for (Neighbor neighbor : neighbors) {
                        if (!neighbor.ip.equals(sourceIP) || neighbor.port != sourcePort) {
                            try (DatagramSocket socket = new DatagramSocket()) {
                                byte[] buf = lookupMessage.getBytes();
                                InetAddress address = InetAddress.getByName(neighbor.ip);
                                DatagramPacket packet = new DatagramPacket(buf, buf.length, address, neighbor.port);
                                socket.send(packet);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                findRequests.add(requestID + fileName);
            }
        }// processLookup method

    }// LookupThread class
    
    class FileTransferThread extends Thread
    {
        ServerSocket serverSocket = null;   // TCP listening socket
        Socket clientSocket = null;         // TCP socket to a client
        DataInputStream in = null;          // input stream from client
        DataOutputStream out = null;        // output stream to client
        String request, reply;

        /* this is the implementation of the peer's File Transfer
           thread, which first creates a listening socket (or welcome
           socket or server socket) and then continuously waits for
           connections. For each connection it accepts, the newly
           created client socket waits for a single request and processes
           it using the helper method below (and is finally closed).
        */      
        public void run() {
            try {
                serverSocket = new ServerSocket(ftPort);
                while (true) {
                    clientSocket = serverSocket.accept();
                    in = new DataInputStream(clientSocket.getInputStream());
                    out = new DataOutputStream(clientSocket.getOutputStream());
    
                    request = in.readUTF();
                    process(request);
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.out.println("Error in FileTransferThread: " + e.getMessage());
            } finally {
                try {
                    if (serverSocket != null) {
                        serverSocket.close();
                    }
                } catch (IOException e) {
                    System.out.println("Error closing server socket: " + e.getMessage());
                }
            }
        }// run method
        
        /* Process the given request received by the TCP client
           socket.  This request must be a "get" message (the only
           command that uses the TCP sockets). If the requested
           file is stored locally, read its contents (as a String)
           using the helper method below and send them to the other side
           in a "fileFound" message. Otherwise, send back a "fileNotFound"
           message.
         */
        void process(String request) {
            StringTokenizer tokenizer = new StringTokenizer(request);
            String command = tokenizer.nextToken();
            if (command.equals("get")) {
                String fileName = tokenizer.nextToken();
                File file = new File(filesPath + File.separator + fileName);
                if (file.exists()) {
                    byte[] fileContents = readFile(file);
                    try {
                        openStreams();
                        out.writeUTF("fileFound");
                        out.writeInt(fileContents.length);
                        out.write(fileContents);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        close();
                    }
                } else {
                    try {
                        openStreams();
                        out.writeUTF("fileNotFound");
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        close();
                    }
                }
            } else {
                System.out.println("Invalid request received: " + request);
            }
        }// process method

        /* Given a File object for a file that we know is stored at this
           peer, return the contents of the file as a byte array.
        */
        byte[] readFile(File file) {
            byte[] fileContent = null;
            try {
                FileInputStream fileInputStream = new FileInputStream(file);
                fileContent = new byte[(int) file.length()];
                fileInputStream.read(fileContent);
                fileInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return fileContent;
        }// readFile method

        /* Open the necessary I/O streams and initialize the in and out
           variables; this method does not catch any exceptions.
        */
        void openStreams() throws IOException {
            out = new DataOutputStream(clientSocket.getOutputStream());
            in = new DataInputStream(clientSocket.getInputStream());
        }// openStreams method

        /* close all open I/O streams and the client socket
         */
        void close() {
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
                if (clientSocket != null) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }// close method
        
    }// FileTransferThread class

}// Peer class
