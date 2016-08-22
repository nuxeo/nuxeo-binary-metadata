/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *      Vladimir Pasquier <vpasquier@nuxeo.com>
 */
package org.nuxeo.binary.metadata.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.binary.metadata.api.BinaryMetadataService;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;

/**
 * @since 7.1
 */
@RunWith(FeaturesRunner.class)
@Features(BinaryMetadataFeature.class)
@LocalDeploy({ "org.nuxeo.binary.metadata:binary-metadata-contrib-test.xml",
        "org.nuxeo.binary.metadata:binary-metadata-disable-listener.xml",
        "org.nuxeo.binary.metadata:binary-metadata-contrib-pdf-test.xml" })
@RepositoryConfig(cleanup = Granularity.METHOD, init = BinaryMetadataServerInit.class)
public class TestBinaryMetadataService {

    @Inject
    BinaryMetadataService binaryMetadataService;

    @Inject
    CoreSession session;

    List<String> musicMetadata = new ArrayList<String>() {

        private static final long serialVersionUID = 1L;

        {
            add("ID3:Title");
            add("ID3:Lyrics-por");
            add("ID3:Publisher");
            add("ID3:Comment");
        }
    };

    List<String> PSDMetadata = new ArrayList<String>() {

        private static final long serialVersionUID = 1L;

        {
            add("EXIF:ImageHeight");
            add("EXIF:Software");
            add("IPTC:Keywords");
            add("EXIF:DateTimeOriginal");
        }
    };

    private static final Map<String, Object> inputPSDMetadata;

    static {
        inputPSDMetadata = new HashMap<>();
        inputPSDMetadata.put("EXIF:ImageHeight", "200");
        inputPSDMetadata.put("EXIF:Software", "Nuxeo");
        inputPSDMetadata.put("IPTC:Keywords", new String[] {"keyword1", "keyword2"});
        inputPSDMetadata.put("IPTC:Keywords", new String[] {"keyword1", "keyword2"});
        try {
            Date date = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss").parse("2018:06:15 00:00:00");
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            inputPSDMetadata.put("EXIF:DateTimeOriginal", calendar);
        } catch (ParseException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void itShouldExtractAllMetadataFromBinary() {
        // Get the document with MP3 attached
        DocumentModel musicFile = BinaryMetadataServerInit.getFile(0, session);
        BlobHolder musicBlobHolder = musicFile.getAdapter(BlobHolder.class);
        Map<String, Object> blobProperties = binaryMetadataService.readMetadata(musicBlobHolder.getBlob(), false);
        assertNotNull(blobProperties);
        assertEquals("Twist", blobProperties.get("ID3:Title").toString());
        assertEquals("Divine Recordings", blobProperties.get("ID3:Publisher").toString());
    }

    @Test
    public void itShouldExtractGivenMetadataFromBinary() {
        // Get the document with MP3 attached
        DocumentModel musicFile = BinaryMetadataServerInit.getFile(0, session);
        BlobHolder musicBlobHolder = musicFile.getAdapter(BlobHolder.class);
        Map<String, Object> blobProperties = binaryMetadataService.readMetadata(musicBlobHolder.getBlob(),
                musicMetadata, false);
        assertNotNull(blobProperties);
        assertEquals(4, blobProperties.size());
        assertEquals("Twist", blobProperties.get("ID3:Title").toString());
        assertEquals("Divine Recordings", blobProperties.get("ID3:Publisher").toString());
    }

    @Test
    public void itShouldWriteGivenMetadataInBinary() {
        // Get the document with PSD attached
        DocumentModel psdFile = BinaryMetadataServerInit.getFile(3, session);
        BlobHolder psdBlobHolder = psdFile.getAdapter(BlobHolder.class);

        // Check the content
        Map<String, Object> blobProperties = binaryMetadataService.readMetadata(psdBlobHolder.getBlob(), PSDMetadata,
                false);
        assertNotNull(blobProperties);
        assertEquals(2, blobProperties.size());
        assertEquals(100, blobProperties.get("EXIF:ImageHeight"));
        assertEquals("Adobe Photoshop CS4 Macintosh", blobProperties.get("EXIF:Software").toString());
        assertNull(blobProperties.get("EXIF:DateTimeOriginal"));

        // Write a new content
        Blob blob = binaryMetadataService.writeMetadata(psdBlobHolder.getBlob(), inputPSDMetadata, false);
        assertNotNull(blob);

        // Check the content
        blobProperties = binaryMetadataService.readMetadata(blob, PSDMetadata, false);
        assertNotNull(blobProperties);
        assertEquals(4, blobProperties.size());
        assertEquals(200, blobProperties.get("EXIF:ImageHeight"));
        assertEquals("Nuxeo", blobProperties.get("EXIF:Software").toString());
        // Check keywords were written to the binary
        List<String> keywords = (List<String>) blobProperties.get("IPTC:Keywords");
        assertEquals("keyword1", keywords.get(0));
        assertEquals("keyword2", keywords.get(1));
        // Check date was written to the binary
        try {
            Date date = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss").parse("2018:06:15 00:00:00");
            assertEquals(date, blobProperties.get("EXIF:DateTimeOriginal"));
        } catch (ParseException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void itShouldWriteDocPropertiesFromBinaryWithMapping() {
        // Get the document with PDF attached.
        DocumentModel pdfDoc = BinaryMetadataServerInit.getFile(1, session);

        // Copy into the document according to metadata mapping contribution.
        binaryMetadataService.writeMetadata(pdfDoc, session);

        // Check if the document has been overwritten by binary metadata.
        pdfDoc = BinaryMetadataServerInit.getFile(1, session);
        assertEquals("en-US", pdfDoc.getPropertyValue("dc:title"));
        assertEquals("OpenOffice.org 3.2", pdfDoc.getPropertyValue("dc:source"));
        assertEquals("Writer", pdfDoc.getPropertyValue("dc:coverage"));
        assertEquals("Mirko Nasato", pdfDoc.getPropertyValue("dc:creator"));

        // Test metadata with lists
        String[] keywords = (String[]) pdfDoc.getPropertyValue("dc:subjects");
        assertEquals("tag1", keywords[0]);
        assertEquals("tag2", keywords[1]);

        // Test if description has been overriden by higher order contribution
        assertEquals("OpenOffice.org 3.2", pdfDoc.getPropertyValue("dc:description"));
    }

    @Test
    public void itShouldAcceptQuoteInMetadataAndAllASCII() {
        // Get the document with MP3 attached
        DocumentModel musicFile = BinaryMetadataServerInit.getFile(0, session);
        BlobHolder musicBlobHolder = musicFile.getAdapter(BlobHolder.class);
        Map<String, Object> blobProperties = binaryMetadataService.readMetadata(musicBlobHolder.getBlob(), true);
        assertNotNull(blobProperties);
        assertEquals("Twist", blobProperties.get("Title").toString());

        // Write Non ASCII Character
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("SourceURL", "l'adresse idéale");
        try {
            binaryMetadataService.writeMetadata(musicBlobHolder.getBlob(), metadata, true);
        } catch (IllegalArgumentException e) {
            fail(e.getMessage());
        }
    }
}
