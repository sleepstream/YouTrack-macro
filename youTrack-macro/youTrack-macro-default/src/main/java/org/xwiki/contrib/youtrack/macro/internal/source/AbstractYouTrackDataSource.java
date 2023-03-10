/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.youtrack.macro.internal.source;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.xwiki.contrib.youtrack.config.YouTrackConfiguration;
import org.xwiki.contrib.youtrack.config.YouTrackServer;
import org.xwiki.contrib.youtrack.macro.YouTrackDataSource;
import org.xwiki.contrib.youtrack.macro.YouTrackMacroParameters;
import org.xwiki.contrib.youtrack.macro.internal.source.jsonData.ItemObject;
import org.xwiki.rendering.macro.MacroExecutionException;

import javax.inject.Inject;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;

/**
 * Common implementation for YouTrack Data Source that knowns how to execute
 * a JQL query on a YouTrack instance and retrieve the
 * list of matching YouTrack issues.
 *
 * @version $Id$
 * @since 4.2M1
 */
public abstract class AbstractYouTrackDataSource implements YouTrackDataSource
{
    /**
     * URL Prefix to use to build the full JQL URL (doesn't contain the JQL query itself which needs to be appended).
     */
    private static final String JQL_URL_PREFIX =
        "api/issues/";

    @Inject
    private YouTrackConfiguration configuration;

    @Inject
    private Logger logger;

    @Inject
    private HTTPYouTrackFetcher youtrackFetcher;

    @Inject
    private YouTrackServerResolver youtrackServerResolver;

    /**
     * @param jsonObject the XML document from which to extract YouTrack issues
     * @param youTrackServer
     * @return the list of XML Elements for each YouTrack issue, indexed in a Map with the issue id as the key
     */
    protected List<ItemObject> buildIssues(JsonObject jsonObject, YouTrackServer youTrackServer)
    {

        Gson gson = new GsonBuilder().create();
        ItemObject item = gson.fromJson(jsonObject, ItemObject.class);

        item.setLink(youTrackServer.getURL() + "issue/" + item.getId());

        return Collections.singletonList(item);
    }

    /**
     * @param youTrackServer the YouTrack Server definition to use
     * @param jqlQuery the JQL query to execute
     * @param maxCount the max number of issues to get
     * @return the XML document containing the matching YouTrack issues
     * @throws MacroExecutionException if the YouTrack issues cannot be retrieved
     */
    public JsonObject getJsonDocument(YouTrackServer youTrackServer, String jqlQuery, int maxCount)
        throws MacroExecutionException
    {
        JsonObject document;
        String urlString = computeFullURL(youTrackServer, jqlQuery, maxCount);
        try {
            document = this.youtrackFetcher.fetch(urlString, youTrackServer);
        } catch (Exception e) {
            throw new MacroExecutionException(String.format("Failed to retrieve YouTrack data from [%s] for JQL [%s] "
                            + "url [%s]",
                youTrackServer.getURL(), jqlQuery, urlString), e);
        }
        return document;
    }

    protected String computeFullURL(YouTrackServer youTrackServer, String jqlQuery, int maxCount)
    {
        StringBuilder additionalQueryString = new StringBuilder();

        // Restrict number of issues returned if need be
//        if (maxCount > -1) {
//            additionalQueryString.append("&tempMax=").append(maxCount);
//        }

        additionalQueryString.append("?fields=id,idReadable,summary,type,created,updated,resolved,"
                + "customFields(name,value(name))"
                + "&customFields=type&customFields=assignee&customFields=priority&customFields=state");
//                + "&customFields=fix+versions");

        // Note: we encode using UTF8 since it's the W3C recommendation.
        // See http://www.w3.org/TR/html40/appendix/notes.html#non-ascii-chars
        String fullURL = String.format("%s%s%s%s", youTrackServer.getURL(), JQL_URL_PREFIX, encode(jqlQuery),
            additionalQueryString);
        this.logger.debug("Computed YouTrack URL [{}]", fullURL);

        return fullURL;
    }

    /**
     * @param parameters the macro's parameters
     * @return the url to the YouTrack instance (eg "http://youtrack.xwiki.org")
     * @throws MacroExecutionException if no URL has been specified (either in the macro parameter or configuration)
     */
    protected YouTrackServer getYouTrackServer(YouTrackMacroParameters parameters) throws MacroExecutionException
    {
        return this.youtrackServerResolver.resolve(parameters);
    }

    private String encode(String content)
    {
        try {
            return URLEncoder.encode(content, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Missing UTF-8 encoding", e);
        }
    }
}
