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

package com.symphony.models;

/**
 * Symphony User
 *
 * Represents a Symphony User. Contains more information that just User from Symphony SDK
 * i.e.: "emailAddress" and "displayName". Class should be used for enriching a Symphony User
 */

public interface ISymphonyUser {

  Long getUserId();

  String getEmailAddress();

  String getDisplayName();

  @Override
  public int hashCode();

  @Override
  public boolean equals(Object other);
}