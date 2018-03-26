/*
 * Copyright (C) 2013 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.glassware;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.mirror.Mirror;
import com.google.api.services.mirror.model.Location;
import com.google.api.services.mirror.model.MenuItem;
import com.google.api.services.mirror.model.Notification;
import com.google.api.services.mirror.model.NotificationConfig;
import com.google.api.services.mirror.model.TimelineItem;
import com.google.api.services.mirror.model.UserAction;
import com.google.common.collect.Lists;

import com.sforce.soap.enterprise.Connector;
import com.sforce.soap.enterprise.DeleteResult;
import com.sforce.soap.enterprise.EnterpriseConnection;
import com.sforce.soap.enterprise.Error;
import com.sforce.soap.enterprise.QueryResult;
import com.sforce.soap.enterprise.SaveResult;
import com.sforce.soap.enterprise.sobject.SObject;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;
import com.sforce.ws.wsdl.Binding;

import com.sforce.soap.enterprise.sobject.Account;
import com.sforce.soap.enterprise.sobject.Attachment;
import com.sforce.soap.enterprise.sobject.Contact;


import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

/**
 * Handles the notifications sent back from subscriptions
 * 
 * @author Jenny Murphy - http://google.com/+JennyMurphy
 */
public class NotifyServlet extends HttpServlet {
  private static final Logger LOG = Logger.getLogger(MainServlet.class.getSimpleName());

  static final String USERNAME = "wyeh@glasstest.org";
  static final String PASSWORD = "isvforce123";
    static EnterpriseConnection connection;
  
  
  
  /*
  public String getJWT(){
	  
	  
	  try {
		HttpPost post = new HttpPost("http://chatteroem.herokuapp.com/people/string");  

		post.setHeader("text", "post");
		System.out.println("pass1");
		// add parameters to the post method  
		List <NameValuePair> parameters = new ArrayList <NameValuePair>();   
		parameters.add(new BasicNameValuePair("text","text"));
		UrlEncodedFormEntity sendentity = new UrlEncodedFormEntity(parameters, HTTP.UTF_8);  
		post.setEntity(sendentity);   
		// create the client and execute the post method  
		HttpClient client = new DefaultHttpClient();  
		HttpResponse response1 = client.execute(post);  
		return response1.toString();
    	
	  }
	  
		catch (Exception e) {
			e.printStackTrace();
			return "fail";
		}
	  
  }
  
  */
  
  
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    // Respond with OK and status 200 in a timely fashion to prevent redelivery
    response.setContentType("text/html");
    Writer writer = response.getWriter();
    writer.append("OK");
    writer.close();

    // Get the notification object from the request body (into a string so we
    // can log it)
    BufferedReader notificationReader =
        new BufferedReader(new InputStreamReader(request.getInputStream()));
    String notificationString = "";

    // Count the lines as a very basic way to prevent Denial of Service attacks
    int lines = 0;
    while (notificationReader.ready()) {
      notificationString += notificationReader.readLine();
      lines++;

      // No notification would ever be this long. Something is very wrong.
      if(lines > 1000) {
        throw new IOException("Attempted to parse notification payload that was unexpectedly long.");
      }
    }

    LOG.info("got raw notification " + notificationString);

    JsonFactory jsonFactory = new JacksonFactory();

    // If logging the payload is not as important, use
    // jacksonFactory.fromInputStream instead.
    Notification notification = jsonFactory.fromString(notificationString, Notification.class);

    LOG.info("Got a notification with ID: " + notification.getItemId());

    // Figure out the impacted user and get their credentials for API calls
    String userId = notification.getUserToken();
    Credential credential = AuthUtil.getCredential(userId);
    Mirror mirrorClient = MirrorClient.getMirror(credential);


    if (notification.getCollection().equals("locations")) {
      LOG.info("Notification of updated location");
      Mirror glass = MirrorClient.getMirror(credential);
      // item id is usually 'latest'
      Location location = glass.locations().get(notification.getItemId()).execute();

      LOG.info("New location is " + location.getLatitude() + ", " + location.getLongitude());
      MirrorClient.insertTimelineItem(
          credential,
          new TimelineItem()
              .setText("You are now at " + location.getLatitude() + ", " + location.getLongitude())
              .setNotification(new NotificationConfig().setLevel("DEFAULT")).setLocation(location)
              .setMenuItems(Lists.newArrayList(new MenuItem().setAction("NAVIGATE"))));

      // This is a location notification. Ping the device with a timeline item
      // telling them where they are.
    } else if (notification.getCollection().equals("timeline")) {
      
    
    	
    //	getJWT();
    	
    	
    	
    	
    	
    	
    	// Get the impacted timeline item
      TimelineItem timelineItem = mirrorClient.timeline().get(notification.getItemId()).execute();
      LOG.info("Notification impacted timeline item with ID: " + timelineItem.getId());

      // If it was a share, and contains a photo, bounce it back to the user.
      if (notification.getUserActions().contains(new UserAction().setType("SHARE"))
          && timelineItem.getAttachments() != null && timelineItem.getAttachments().size() > 0) {
        LOG.info("It was a share of a photo. Sending the photo back to the user.");

        // Get the first attachment
        String attachmentId = timelineItem.getAttachments().get(0).getId();
        LOG.info("Found attachment with ID " + attachmentId);

        // Get the attachment content
        InputStream stream =
            MirrorClient.getAttachmentInputStream(credential, timelineItem.getId(), attachmentId);

        // Create a new timeline item with the attachment
        TimelineItem echoPhotoItem = new TimelineItem();
        echoPhotoItem.setNotification(new NotificationConfig().setLevel("DEFAULT"));
        echoPhotoItem.setText("Echoing your shared photo");
//NOW
        ConnectorConfig config = new ConnectorConfig();
        config.setUsername(USERNAME);
        config.setPassword(PASSWORD);
        try {

            connection = Connector.newConnection(config);

            // display some current settings
            System.out.println("Auth EndPoint: "+config.getAuthEndpoint());
            System.out.println("Service EndPoint: "+config.getServiceEndpoint());
            System.out.println("Username: "+config.getUsername());
            System.out.println("SessionId: "+config.getSessionId());
        } catch (ConnectionException e1) {
            e1.printStackTrace();
        }  

        
        
      
        		ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        		int nRead;
        		byte[] data = new byte[16384];

        		while ((nRead = stream.read(data, 0, data.length)) != -1) {
        		  buffer.write(data, 0, nRead);
        		}

        		buffer.flush();

        		byte[] sizes =	 buffer.toByteArray();
        
        
        
        
        
     //   EnterpriseConnection binding = null;
        try {

        	byte[] inbuff = sizes;       
            stream.read(inbuff);

            Attachment attach = new Attachment();
            attach.setBody(inbuff);
            attach.setName("attachment.jpg");
            attach.setIsPrivate(false);
            // attach to an object in SFDC 
            attach.setParentId("a00i0000007GFuM");


			SaveResult sr = connection.create(new com.sforce.soap.enterprise.sobject.SObject[] {attach})[0];
           
            if (sr.isSuccess()) {
                System.out.println("Successfully added attachment.");
            } else {
                System.out.println("Error adding attachment: ");
            }


        } catch (FileNotFoundException fnf) {
            System.out.println("File Not Found: " +fnf.getMessage());

        } catch (IOException io) {
            System.out.println("IO: " +io.getMessage());            
        } catch (ConnectionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        
        
        
        
        
        
//END        
        MirrorClient.insertTimelineItem(credential, echoPhotoItem, "image/jpeg", stream);

      } else {
        LOG.warning("I don't know what to do with this notification, so I'm ignoring it.");
      }
    }
  }
}
