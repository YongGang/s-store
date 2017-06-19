/***************************************************************************
 *  Copyright (C) 2017 by S-Store Project                                  *
 *  Brown University                                                       *
 *  Massachusetts Institute of Technology                                  *
 *  Portland State University                                              *
 *                                                                         *
 *  Author:  The S-Store Team (sstore.cs.brown.edu)                        *                                   
 *                                                                         *
 *                                                                         *
 *  Permission is hereby granted, free of charge, to any person obtaining  *
 *  a copy of this software and associated documentation files (the        *
 *  "Software"), to deal in the Software without restriction, including    *
 *  without limitation the rights to use, copy, modify, merge, publish,    *
 *  distribute, sublicense, and/or sell copies of the Software, and to     *
 *  permit persons to whom the Software is furnished to do so, subject to  *
 *  the following conditions:                                              *
 *                                                                         *
 *  The above copyright notice and this permission notice shall be         *
 *  included in all copies or substantial portions of the Software.        *
 *                                                                         *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,        *
 *  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF     *
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. *
 *  IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR      *
 *  OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,  *
 *  ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR  *
 *  OTHER DEALINGS IN THE SOFTWARE.                                        *
 ***************************************************************************/
package edu.brown.stream;

import java.net.*;
import java.util.ArrayList;
import java.io.*;
import java.lang.System;

public class VoteGenServer {
    
    public static void main(String[] vargs) throws Exception {

         if( vargs.length == 0 )
        return;

         String filename = vargs[0];
         System.out.println(filename);

         edu.brown.stream.VoteGenerator switchboard;
         try{
            switchboard = new edu.brown.stream.VoteGenerator(filename);
         }
         catch(Exception ex)
         {
             System.out.println(filename + " is not a valid vote file!");
             return;
         }

         String clientRequest;
         String voteContent;
         ServerSocket welcomeSocket = new ServerSocket(6789);
         
//         ArrayList<Socket> sockets = new ArrayList<Socket>();

         while(true)
         {
            //
//            int size = sockets.size();
//            if( size >= 100 )
//            {
//                for(int i=0;i<size;i++)
//                {
//                    Socket s = sockets.get(i);
//                    s.close();
//                }
//                sockets.clear();
//            }
             
            //
            System.out.println("Waiting for connection ... ");
            Socket connectionSocket = welcomeSocket.accept();
            BufferedReader inFromClient =
               new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
            DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
            clientRequest = inFromClient.readLine();
            System.out.println("Received: " + clientRequest);
            if(clientRequest.equals("n")==true)
            {
                edu.brown.stream.PhoneCallGenerator.PhoneCall call = switchboard.nextVote();
                               
                if(call != null)    
                    voteContent = call.getContent();
                else
                    voteContent = "0";
                
                System.out.println("Reponse: " + voteContent);
                outToClient.writeBytes(voteContent + "\n");
                outToClient.flush();
                
            }
            else
            {
//                if(clientRequest.equals("e")==true)
//                {
//                    //connectionSocket.close();
//                    //sockets.add(connectionSocket);
//                }
//                else
                {
                    voteContent = "-1"; // this is indicate invalid result
                    System.out.println("Reponse: " + voteContent);
                    outToClient.writeBytes(voteContent + "\n");
                    outToClient.flush();
                }
            }
            
            connectionSocket.close();
            
         }
    }

}

//package edu.brown.stream;
//
//import java.net.*;
//import java.io.*;
//import java.lang.System;
//import java.text.SimpleDateFormat;
//import java.util.ArrayList;
//import java.util.Calendar;
//
//import edu.brown.stream.PhoneCallGenerator.PhoneCall;
//
//public class VoteGenServer implements Runnable 
//{
//    
//    private final Socket clientSocket;
//    private edu.brown.stream.VoteGenerator switchboard;
//
//    public VoteGenServer(Socket clientsocket, edu.brown.stream.VoteGenerator switchboard)
//    {
//        this.clientSocket = clientsocket;
//        this.switchboard = switchboard;
//    }
//    
//    private synchronized String getNextVote()
//    {
//        String response;
//        
//        edu.brown.stream.PhoneCallGenerator.PhoneCall call = switchboard.nextVote();
//        
//        if(call != null)    
//            response = call.getContent();
//        else
//            response = "0";
//        
//        return response;
//    }
//    
//    public void run()
//    {
//       try{
//           boolean beContinue = true;
//    
//           BufferedReader inFromClient = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
//           DataOutputStream outToClient = new DataOutputStream(this.clientSocket.getOutputStream());
//    
//           String request, response;
//    
//           response = "-1";
//           String timeLog;
//           
//           while(beContinue==true)
//           {
//               synchronized (response) {
//                   request = inFromClient.readLine();
//                   timeLog = new SimpleDateFormat("yyyyMMdd_HH:mm:ss.SSS").format(Calendar.getInstance().getTime());
//                   //System.out.println("Received: " + request);
//                   if(request==null)
//                       continue;
//                   
//                   if(request.equals("n")==true)
//                   {
//                       response = getNextVote();
//                       
//                   }
//                   else
//                   {
//                       if(request.equals("exit")==true)
//                       {
//                           response = "-1";
//                           beContinue = false;
//                       }
//                       else
//                           response = "-1"; // this is indicate invalid result
//                   }
//                   
//                   System.out.println(timeLog + " - Response: " + response);
//                   outToClient.writeBytes(response + "\n");
//               }
//           }
//       }
//       catch (Exception e)
//       {
//           e.printStackTrace();
//       }
//    }
//    
//    
//    public static void main(String[] vargs) throws Exception {
//
//         if( vargs.length == 0 )
//	    return;
//
//         String filename = vargs[0];
//         System.out.println(filename);
//
//         edu.brown.stream.VoteGenerator switchboard;
//         try{
//         	switchboard = new edu.brown.stream.VoteGenerator(filename);
//         }
//         catch(Exception ex)
//         {
//             System.out.println(filename + " is not a valid vote file!");
//             return;
//         }
//
//         ServerSocket welcomeSocket = new ServerSocket(6789);
//
//         while(true)
//         {
//            Socket connectionSocket = welcomeSocket.accept();
//            new Thread(new VoteGenServer(connectionSocket, switchboard)).start();
//         }
//
//    }
//
//}
