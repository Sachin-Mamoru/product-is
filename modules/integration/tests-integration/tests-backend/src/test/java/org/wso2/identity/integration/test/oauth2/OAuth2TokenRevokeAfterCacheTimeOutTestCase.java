/*
* Copyright (c) WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
* WSO2 Inc. licenses this file to you under the Apache License,
* Version 2.0 (the "License"); you may not use this file except
* in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.identity.integration.test.oauth2;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.annotations.ExecutionEnvironment;
import org.wso2.carbon.automation.engine.annotations.SetEnvironment;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.carbon.identity.oauth.stub.dto.OAuthConsumerAppDTO;
import org.wso2.carbon.integration.common.utils.mgt.ServerConfigurationManager;
import org.wso2.identity.integration.test.util.Utils;

import java.io.File;
import java.util.ArrayList;

public class OAuth2TokenRevokeAfterCacheTimeOutTestCase extends OAuth2ServiceAbstractIntegrationTest {
    private String consumerKey;
    private String consumerSecret;
    private static final String TOKEN_API_ENDPOINT = "https://localhost:9853/oauth2/token";
    private static final String REVOKE_TOKEN_API_ENDPOINT = "https://localhost:9853/oauth2/revoke";

    @BeforeClass(alwaysRun = true)
    public void testInit() throws Exception {
        super.init();
        setSystemproperties();
        changeISConfiguration();

        super.init(TestUserMode.SUPER_TENANT_USER);
    }

    /**
     * This tests written for test for token revocation after cache timed out CARBON-15028
     * This test needed two APIM nodes with clustering enabled
     * During the test one node is use to generate the token and other node use to revoke the token
     * After cache timeout new token should issued after it revoked
     *
     * @throws Exception
     */
    @SetEnvironment(executionEnvironments = { ExecutionEnvironment.ALL })
    @Test(description = "Revoke token after cache timed out")
    public void testRevokeTokenAfterCacheTimedOut() throws Exception {
        //Application utils
        OAuthConsumerAppDTO appDto = createApplication();
        consumerKey = appDto.getOauthConsumerKey();
        consumerSecret = appDto.getOauthConsumerSecret();
        //request for token
        String token = requestAccessToken(consumerKey, consumerSecret, TOKEN_API_ENDPOINT,
                "admin", "admin", null);
        //Sleep for 1m for cache timeout
        Thread.sleep(60 * 1000);
        //Revoke access token
        revokeAccessToken(consumerKey, consumerSecret, token, REVOKE_TOKEN_API_ENDPOINT);
        //Generate new token
        String newToken = requestAccessToken(consumerKey, consumerSecret, TOKEN_API_ENDPOINT,
                "admin", "admin", null);
        Assert.assertNotEquals(token, newToken, "Token revocation failed");
    }

    @AfterClass(alwaysRun = true)
    public void atEnd() throws Exception {
        super.init(TestUserMode.SUPER_TENANT_USER);
        deleteApplication();
        removeOAuthApplicationData();
        resetISConfiguration();
        consumerKey = null;
    }

    /**
     * Revoke access token from the given token generation endpoint
     *
     * @param consumerKey    consumer key of the application
     * @param consumerSecret consumer secret of the application
     * @param backendUrl     token generation API endpoint
     * @param accessToken    access token to be revoked
     * @throws Exception if something went wrong when requesting token
     */
    public void revokeAccessToken(String consumerKey, String consumerSecret,
                                  String accessToken, String backendUrl) throws Exception {
        ArrayList<NameValuePair> postParameters;
        HttpClient client = new DefaultHttpClient();
        HttpPost httpRevoke = new HttpPost(backendUrl);
        //Generate revoke token post request
        httpRevoke.setHeader("Authorization", "Basic " + getBase64EncodedString(consumerKey, consumerSecret));
        httpRevoke.setHeader("Content-Type", "application/x-www-form-urlencoded");
        postParameters = new ArrayList<NameValuePair>();
        postParameters.add(new BasicNameValuePair("token", accessToken));
        httpRevoke.setEntity(new UrlEncodedFormEntity(postParameters));
        client.execute(httpRevoke);
    }

    /**
     * Change the server configuration to reduce the idle cache time out for 1 minute
     *
     * @throws Exception
     */
    private void changeISConfiguration() throws Exception {
        log.info("Replacing repository/conf/tomcat/carbon/WEB-INF/web.xml reducing idle cache timeout");

        String carbonHome = Utils.getResidentCarbonHome();
        File webXml = new File(carbonHome + File.separator
                               + "repository" + File.separator + "conf" + File.separator + "tomcat" + File.separator
                               + "carbon" + File.separator + "WEB-INF" + File.separator + "web.xml");
        File configuredWebXml = new File(getISResourceLocation()
                                              + File.separator + "oauth" + File.separator
                                              + "web.xml");

        ServerConfigurationManager serverConfigurationManager = new ServerConfigurationManager(isServer);
        serverConfigurationManager.applyConfigurationWithoutRestart(configuredWebXml, webXml, true);
        serverConfigurationManager.restartGracefully();
    }

    /**
     * Restore  the server configuration
     *
     * @throws Exception
     */
    private void resetISConfiguration() throws Exception{
        log.info("Replacing default configurations");
        ServerConfigurationManager serverConfigurationManager = new ServerConfigurationManager(isServer);
        serverConfigurationManager.restoreToLastConfiguration();
    }
}
