package bench;

import index.server.Assign;
import index.server.IndexingServer;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import node.Peer;
import node.client.Client;
import util.DistributedHashtable;
import util.Util;

public class Bench {
	
	private static ArrayList<String> peerList;
	private static ArrayList<String> serverList;

	public static void main(String[] args) {
		
		try {
			peerList = DistributedHashtable.readConfigFile("peers");
			serverList = DistributedHashtable.readConfigFile("servers");
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		String[] serverAddress, peerAddress;
		int port;
		for(String server : serverList){
			serverAddress = server.split(":");
			port = Integer.parseInt(serverAddress[1]);

			ServerSocket serverSocket = null;
			try {
				serverSocket = new ServerSocket(port);
			} catch (IOException e) {
				e.printStackTrace();
			}

			// start server
			IndexingServer indexserver = new IndexingServer(serverSocket);
			indexserver.start();

			// start assign server
			Assign assign = new Assign(indexserver);
			assign.start();
		}
		
		ArrayList<Client> clients = new ArrayList<Client>();
		
		for(int id = 0; id <  peerList.size(); id++) {
			peerAddress = peerList.get(id).split(":");
			String address = peerAddress[0];
			port = Integer.parseInt(peerAddress[1]);

			String dir = peerAddress[2];
			File folder = new File(dir);

			if (!folder.isDirectory()) {
				System.out.println("Put a valid directory name");
				return;
			}

			ArrayList<String> fileNames = Util.listFilesForFolder(folder);
			Peer peer = null;
			try {
				peer = new Peer(id, address, port, dir, fileNames,
						fileNames.size());
			} catch (IOException e) {
				e.printStackTrace();
			}

			Client c = new Client(peer);
			c.startOpenServer();
			
			ArrayList<Socket> serverSocketList = new ArrayList<Socket>();
			
			for (int i = 0; i < serverList.size(); i++) {
				serverAddress = serverList.get(i).split(":");
				address = serverAddress[0];
				port = Integer.parseInt(serverAddress[1]);

				try {
					System.out.println("Testing connection to server " + address
							+ ":" + port);
					Socket s = new Socket(address, port);
					serverSocketList.add(s);
					System.out.println("Server " + address + ":" + port
							+ " is running.");
				} catch (Exception e) {
					i--;
					port--;
				}
			}
			c.setServerSocketList(serverSocketList);
			
			c.setPeerSocketList(new Socket[peerList.size()]);
			clients.add(c);
		}
		
		long start = System.currentTimeMillis();
		
		for(Client client : clients){
			try {
				client.registry(false);
				client.registry(true);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			while(true){
				int id = client.getPeer().getPeerId();
				try {
					client.replicateFiles(id == peerList.size() - 1 ? 0 : id + 1);
					break;
				} catch (Exception e) {
					e.printStackTrace();
					System.out.println("Cannot replicate, trying again...");
					try { Thread.sleep(5000); } catch (Exception e1) { } 
				}
			}
		}
		
		System.out.println("Time for registry 8 clients " + (System.currentTimeMillis() - start) + "ms.");

	}

}
