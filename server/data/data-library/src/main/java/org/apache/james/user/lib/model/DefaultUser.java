/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.user.lib.model;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.mail.MessagingException;
import javax.mail.internet.MimeUtility;

import org.apache.james.core.Username;
import org.apache.james.user.api.model.User;

/**
 * Implementation of User Interface. Instances of this class do not allow the
 * the user name to be reset.
 */
public class DefaultUser implements User, Serializable {

    private static final long serialVersionUID = 5178048915868531270L;

    private final Username userName;
    private String hashedPassword;
    private Algorithm currentAlgorithm;
    private final Algorithm preferredAlgorithm;

    /**
     * Standard constructor.
     * 
     * @param name
     *            the String name of this user
     * @param verifyAlg
     *            the algorithm used to verify the hash of the password
     * @param updateAlg
     *            the algorithm used to update the hash of the password
     */
    public DefaultUser(Username name, Algorithm verifyAlg, Algorithm updateAlg) {
        userName = name;
        currentAlgorithm = verifyAlg;
        preferredAlgorithm = updateAlg;
    }

    /**
     * Constructor for repositories that are construcing user objects from
     * separate fields, e.g. databases.
     * 
     * @param name
     *            the String name of this user
     * @param passwordHash
     *            the String hash of this users current password
     * @param verifyAlg
     *            the algorithm used to verify the hash of the password
     * @param updateAlg
     *            the algorithm used to update the hash of the password
     */
    public DefaultUser(Username name, String passwordHash, Algorithm verifyAlg, Algorithm updateAlg) {
        userName = name;
        hashedPassword = passwordHash;
        currentAlgorithm = verifyAlg;
        preferredAlgorithm = updateAlg;
    }

    @Override
    public Username getUserName() {
        return userName;
    }

    @Override
    public boolean verifyPassword(String pass) {
        try {
            String hashGuess = digestString(pass, currentAlgorithm, userName.asString());
            return hashedPassword.equals(hashGuess);
        } catch (NoSuchAlgorithmException nsae) {
            throw new RuntimeException("Security error: " + nsae);
        }
    }

    @Override
    public boolean setPassword(String newPass) {
        try {
            hashedPassword = digestString(newPass, preferredAlgorithm, userName.asString());
            currentAlgorithm = preferredAlgorithm;
            return true;
        } catch (NoSuchAlgorithmException nsae) {
            throw new RuntimeException("Security error: " + nsae);
        }
    }



    /**
     * Method to access hash of password
     * 
     * @return the String of the hashed Password
     */
    public String getHashedPassword() {
        return hashedPassword;
    }

    /**
     * Method to access the hashing algorithm of the password.
     * 
     * @return the name of the hashing algorithm used for this user's password
     */
    public Algorithm getHashAlgorithm() {
        return currentAlgorithm;
    }

    /**
     * Calculate digest of given String using given algorithm. Encode digest in
     * MIME-like base64.
     *
     * @param pass
     *            the String to be hashed
     * @param algorithm
     *            the algorithm to be used
     * @return String Base-64 encoding of digest
     *
     * @throws NoSuchAlgorithmException
     *             if the algorithm passed in cannot be found
     */
    static String digestString(String pass, Algorithm algorithm, String salt) throws NoSuchAlgorithmException {
        MessageDigest md;
        ByteArrayOutputStream bos;

        try {
            md = MessageDigest.getInstance(algorithm.getName());
            String saltedPass = applySalt(algorithm, pass, salt);
            byte[] digest = md.digest(saltedPass.getBytes(ISO_8859_1));
            bos = new ByteArrayOutputStream();
            OutputStream encodedStream = MimeUtility.encode(bos, "base64");
            encodedStream.write(digest);
            if (!algorithm.isLegacy()) {
                encodedStream.close();
            }
            return bos.toString(ISO_8859_1);
        } catch (IOException | MessagingException e) {
            throw new RuntimeException("Fatal error", e);
        }
    }

    static String applySalt(Algorithm algorithm, String pass, String salt) {
        if (algorithm.isSalted()) {
            return salt + pass;
        } else {
            return pass;
        }
    }
}
