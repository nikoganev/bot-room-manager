/*
 *
 *
 * Copyright 2016 Symphony Communication Services, LLC
 *
 * Licensed to Symphony Communication Services, LLC under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package com.symphony.clients;

import com.symphony.api.agent.api.DatafeedApi;
import com.symphony.api.agent.api.MessagesApi;
import com.symphony.api.agent.client.ApiException;
import com.symphony.api.agent.model.Datafeed;
import com.symphony.api.agent.model.V2Message;
import com.symphony.api.agent.model.V2MessageSubmission;
import com.symphony.api.auth.api.AuthenticationApi;
import com.symphony.api.auth.model.Token;
import com.symphony.api.pod.api.PresenceApi;
import com.symphony.api.pod.api.RoomMembershipApi;
import com.symphony.api.pod.api.StreamsApi;
import com.symphony.api.pod.api.UsersApi;
import com.symphony.api.pod.model.RoomAttributes;
import com.symphony.api.pod.model.RoomCreate;
import com.symphony.api.pod.model.RoomDetail;
import com.symphony.api.pod.model.RoomSearchCriteria;
import com.symphony.api.pod.model.RoomSearchResults;
import com.symphony.api.pod.model.Stream;
import com.symphony.api.pod.model.User;
import com.symphony.api.pod.model.UserId;
import com.symphony.api.pod.model.UserIdList;
import com.symphony.api.pod.model.V2RoomDetail;
import com.symphony.configurations.IConfigurationProvider;
import com.symphony.formatters.MessageML;
import com.symphony.models.ISymphonyMessage;
import com.symphony.models.ISymphonyUser;
import com.symphony.models.Message;
import com.symphony.models.SymphonyMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Interacts with the Symphony platform API
 */

public class SymphonyClient implements ISymphonyClient {

  private static final Logger LOG = LoggerFactory.getLogger(SymphonyClient.class);

  private final AuthenticationApi sbeApi;
  private final AuthenticationApi keyManagerApi;
  private final DatafeedApi datafeedApi;
  private final MessagesApi messagesApi;
  private final PresenceApi presenceApi;
  private final UsersApi usersApi;
  private final StreamsApi streamsApi;
  private final RoomMembershipApi roomMembershipApi;
  private final IConfigurationProvider configurationProvider;
  private final SymphonyRESTClient symphonyRESTClient;

  private Token sessionToken;
  private Token keyManagerToken;
  private Datafeed datafeed;

  public SymphonyClient(IConfigurationProvider configurationProvider) {

    com.symphony.api.agent.client.ApiClient agentClient =
        new com.symphony.api.agent.client.ApiClient();
    com.symphony.api.auth.client.ApiClient keyManagerClient =
        new com.symphony.api.auth.client.ApiClient();
    com.symphony.api.pod.client.ApiClient podApiClient =
        new com.symphony.api.pod.client.ApiClient();
    com.symphony.api.auth.client.ApiClient sbeClient = new com.symphony.api.auth.client.ApiClient();

    podApiClient.setBasePath(configurationProvider.getSymphonyPodPath());
    agentClient.setBasePath(configurationProvider.getSymphonyAgentPath());
    sbeClient.setBasePath(configurationProvider.getSymphonySbePath());
    keyManagerClient.setBasePath(configurationProvider.getSymphonyKeyManagerPath());

    this.messagesApi = new MessagesApi(agentClient);
    this.datafeedApi = new DatafeedApi(agentClient);

    this.keyManagerApi = new AuthenticationApi(keyManagerClient);
    this.sbeApi = new AuthenticationApi(sbeClient);

    this.usersApi = new UsersApi(podApiClient);
    this.presenceApi = new PresenceApi(podApiClient);
    this.streamsApi = new StreamsApi(podApiClient);
    this.roomMembershipApi = new RoomMembershipApi(podApiClient);

    this.symphonyRESTClient = new SymphonyRESTClient(configurationProvider, null, null);
    this.configurationProvider = configurationProvider;

    File certificate = configurationProvider.getCertificateFile();
    System.setProperty("javax.net.ssl.keyStore", certificate.getAbsolutePath());
    System.setProperty("javax.net.ssl.keyStorePassword",
        configurationProvider.getSymphonyKeystorePassword());
    System.setProperty("javax.net.ssl.keyStoreType",
        configurationProvider.getSymphonyKeystoreType());
  }

  /**
   * Authenticates the SymphonyClient
   */
  public void authenticate() {

    try {
      Token sessionToken = sbeApi.v1AuthenticatePost();
      if (sessionToken.getToken() != null && sessionToken.getToken().length() != 0) {
        this.sessionToken = sessionToken;
        this.symphonyRESTClient.setSessionToken(this.sessionToken);

        Token keyManagerToken = keyManagerApi.v1AuthenticatePost();
        if (keyManagerToken.getToken() != null && keyManagerToken.getToken().length() != 0) {
          this.keyManagerToken = keyManagerToken;
          this.symphonyRESTClient.setKeyManagerToken(keyManagerToken);
          LOG.debug("successfully authenticated symphony client");
          return;
        }
      }
    } catch (com.symphony.api.auth.client.ApiException e) {
      throw new RuntimeException("failed to authenticate symphony client", e);
    }
    throw new RuntimeException("failed to authenticate symphony client");
  }

  /**
   * Returns a user associated with that email address
   * @param emailAddress
   * @return
   */
  public User getUserForEmailAddress(String emailAddress) {
    try {
      User user = usersApi.v1UserGet(emailAddress, this.sessionToken.getToken(), true);
      return user;
    } catch (com.symphony.api.pod.client.ApiException e) {
      LOG.error("Could not find user: " + emailAddress, e);
      return null;
    }
  }

  /**
   * Returns users associated with email addresses
   * @param emailAddresses
   * @return
   */
  public List<User> getUsersForEmailAddresses(List<String> emailAddresses) {
    List<User> users = new ArrayList<>();
    for(String emailAddress : emailAddresses) {
      User user = this.getUserForEmailAddress(emailAddress);
      if(user != null) {
        users.add(user);
      }
    }
    return users;
  }

  /**
   * Returns a user associated with that email address
   * @param emailAddress
   * @return
   */
  public ISymphonyUser getSymphonyUserForEmailAddress(String emailAddress) {
    User user = this.getUserForEmailAddress(emailAddress);
    if(user != null) {
      ISymphonyUser symphonyUser = symphonyRESTClient.getUserForId(user.getId());
      return symphonyUser;
    }
    return null;
  }

  /**
   * Returns users associated with email addresses
   * @param emailAddresses
   * @return
   */
  public List<ISymphonyUser> getSymphonyUsersForEmailAddresses(List<String> emailAddresses) {
    List<ISymphonyUser> users = new ArrayList<>();
    for(String emailAddress : emailAddresses) {
      ISymphonyUser user = this.getSymphonyUserForEmailAddress(emailAddress);
      if(user != null) {
        users.add(user);
      }
    }
    return users;
  }

  /**
   * Registers for a datafeed and returns messages post long-polling
   * @return
   */
  public List<Message> getMessages() {
    try {
      if (datafeed == null) {
        datafeed = datafeedApi.v1DatafeedCreatePost(this.sessionToken.getToken(),
            this.keyManagerToken.getToken());
        LOG.debug("Created datafeed");
      }

      List<Message> messages = symphonyRESTClient
          .getMessagesForDataFeed(datafeed.getId());
      return messages;
    } catch (ApiException exception) {
      LOG.error("Error getting message list", exception);
    }

    return new ArrayList<Message>();
  }

  /**
   * Gets messages from Symphony DataFeed API, enriches them with a SymphonyUser and returns it
   * @return
   */
  public List<ISymphonyMessage> getSymphonyMessages() {
    List<ISymphonyMessage> symphonyMessages = new ArrayList<>();
    List<Message> messages = this.getMessages();

    for (Message message : messages) {
      ISymphonyMessage symphonyMessage = this.getSymphonyMessage(message);
      if (symphonyMessage != null) {
        symphonyMessages.add(symphonyMessage);
      }
    }

    return symphonyMessages;
  }

  /**
   * Gets the first message from Symphony DataFeed API and enriches it with a SymphonyUser
   * @return
   */
  public ISymphonyMessage getSymphonyMessage() {
    List<Message> messages = this.getMessages();
    if (messages.size() > 0) {
      ISymphonyMessage symphonyMessage = this.getSymphonyMessage(messages.get(0));
      return symphonyMessage;
    }
    return null;
  }

  /**
   * Helper method that gets the SymphonyUser from the message's senderID
   * @param message
   * @return
   */
  private ISymphonyMessage getSymphonyMessage(Message message) {
    Long senderID = message.getFromUserId();

    if (senderID != null) {
      ISymphonyUser symphonyUser = symphonyRESTClient.getUserForId(senderID);

      if (symphonyUser != null) {
        ISymphonyMessage symphonyMessage = new SymphonyMessage(symphonyUser, message);
        return symphonyMessage;
      }
    } else {
      LOG.error("Null sender ID for message " + message);
    }

    return null;
  }

  /**
   * Creates or returns (if exists) a stream with this User
   * @param user
   * @return
   */
  public Stream getStreamWithUser(User user) {
    return getStreamWithUsers(Collections.singletonList(user));
  }

  /**
   * Creates or returns (if exists) a stream with these Users
   * @param users
   * @return
   */
  public Stream getStreamWithUsers(User... users) {
    List<User> usersList = new ArrayList<User>();
    for (User user : users) {
      if (user != null) {
        usersList.add(user);
      }
    }
    return getStreamWithUsers(usersList);
  }

  @Override
  public V2RoomDetail roomDetailForId(String threadId) {
    try {
      return this.streamsApi.v2RoomIdInfoGet(threadId, sessionToken.getToken());
    }  catch (com.symphony.api.pod.client.ApiException exception) {
      LOG.error("Could not find stream", exception);
      return null;
    }
  }

  /**
   * Creates or returns (if exists) a stream with these Users
   * @param users
   * @return
   */
  public Stream getStreamWithUsers(List<User> users) {
    try {
      UserIdList userIdList = new UserIdList();
      for (User user : users) {
        if (user != null) {
          userIdList.add(user.getId());
        }
      }
      Stream stream = this.streamsApi.v1ImCreatePost(userIdList, sessionToken.getToken());
      return stream;
    } catch (com.symphony.api.pod.client.ApiException exception) {
      LOG.error("Could not create stream", exception);
      return null;
    }
  }

  /**
   * Creates a room for those users with that roomName and returns its roomId
   * If the room already exists, finds that room and returns its roomId
   * @param users
   * @param roomName
   * @return
   */
  public String getRoomWithUsers(List<User> users, String roomName) {
    try {
      UserIdList userIdList = new UserIdList();
      for(User user : users) {
        if(user != null) {
          userIdList.add(user.getId());
        }
      }
      RoomAttributes roomAttributes = new RoomAttributes();
      roomAttributes.setName(roomName);
      roomAttributes.setDescription(roomName);
      roomAttributes.setMembersCanInvite(true);
      roomAttributes.setDiscoverable(true);

      RoomCreate roomCreate = new RoomCreate();
      roomCreate.setRoomAttributes(roomAttributes);
      RoomDetail roomDetail = this.streamsApi.v1RoomCreatePost(roomCreate,
          this.sessionToken.getToken());

      String roomId = roomDetail.getRoomSystemInfo().getId();
      for(User user : users) {
        if(user != null) {
          UserId userId = new UserId();
          userId.setId(user.getId());
          this.roomMembershipApi.v1RoomIdMembershipAddPost(roomId, userId,
              this.sessionToken.getToken());
        }
      }
      LOG.info("Successfully created Room: " + roomId + " with name: " + roomName);
      return roomId;
    } catch (com.symphony.api.pod.client.ApiException exception) {

      if(exception.getLocalizedMessage().contains("Please choose another name.")) {
        V2RoomDetail roomDetail = this.getRoomForSearchQuery(roomName);
        if(roomDetail != null) {
          String threadId = roomDetail.getRoomSystemInfo().getId();
          LOG.debug("Found Room " + roomName + " with threadId: " + threadId);
          return threadId;
        }
      }

      LOG.error("Could not create room", exception);
      return null;
    }
  }

  /**
   * Finds rooms given the query
   * @param query
   * @return
   */
  public List<V2RoomDetail> getRoomsForSearchQuery(String query) {
    RoomSearchCriteria searchCriteria = new RoomSearchCriteria();
    searchCriteria.setQuery(query);

    try {
      RoomSearchResults results =
          streamsApi.v2RoomSearchPost(this.sessionToken.getToken(), searchCriteria, 0, 100);

      if (results.getCount() > 0) {
        return results.getRooms();
      }
    } catch (com.symphony.api.pod.client.ApiException e) {
      throw new RuntimeException("failed while searching for room with query " + query, e);
    }
    throw new RuntimeException("no rooms found for query " + query);
  }

  /**
   * Finds the first room given the query
   * @param query
   * @return
   */
  public V2RoomDetail getRoomForSearchQuery(String query) {
    List<V2RoomDetail> rooms = this.getRoomsForSearchQuery(query);
    return rooms.get(0);
  }

  /**
   * Sends a MessageML to the roomID
   * @param roomID
   * @param messageML
   * @return
   */
  public V2Message sendMessage(String roomID, MessageML messageML) {
    V2MessageSubmission messageSubmission = new V2MessageSubmission();
    messageSubmission.setFormat(V2MessageSubmission.FormatEnum.MESSAGEML);
    messageSubmission.setMessage(messageML.toString());

    V2Message response = this.sendMessage(roomID, messageSubmission);
    return response;
  }

  /**
   * Sends plain text to the roomID
   * @param roomID
   * @param text
   * @return
   */
  public V2Message sendMessage(String roomID, String text) {
    V2MessageSubmission messageSubmission = new V2MessageSubmission();
    messageSubmission.setFormat(V2MessageSubmission.FormatEnum.TEXT);
    messageSubmission.setMessage(text);

    V2Message response = this.sendMessage(roomID, messageSubmission);
    return response;
  }

  /**
   * Sends a MessageML to the room
   * @param roomDetail
   * @param messageML
   * @return
   */
  public V2Message sendMessage(V2RoomDetail roomDetail, MessageML messageML) {
    String roomID = roomDetail.getRoomSystemInfo().getId();
    return this.sendMessage(roomID, messageML);
  }

  /**
   * Private helper method to send a message to a room
   * @param roomID
   * @param message
   * @return
   */
  private V2Message sendMessage(String roomID, V2MessageSubmission message) {

    if (message.getMessage().replaceAll(" ", "").length() == 0) {
      return null;
    }

    try {
      //Synchronized to only send one message at a time
      synchronized (messagesApi) {
        V2Message result = messagesApi.v2StreamSidMessageCreatePost(roomID,
            sessionToken.getToken(), keyManagerToken.getToken(), message);

        if (result != null && result.getId() != null) {
          LOG.debug("successfully sent message: " + message);
          return result;
        }
      }
    } catch (com.symphony.api.agent.client.ApiException e) {
      LOG.error("Failed to send message: " + message, e);
      //throw new RuntimeException("failed while sending message: " + message, e);
    }
    LOG.error("Failed to send message: " + message);
    return null;
  }
}