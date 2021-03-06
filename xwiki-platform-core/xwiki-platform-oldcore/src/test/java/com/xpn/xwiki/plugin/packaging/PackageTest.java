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
package com.xpn.xwiki.plugin.packaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;

import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * Unit tests for the {@link com.xpn.xwiki.plugin.packaging.Package} class.
 * 
 * @version $Id$
 */
public class PackageTest extends AbstractPackageTest
{
    private Package pack;

    @BeforeEach
    protected void beforeEach() throws Exception
    {
        this.pack = new Package();

        doReturn(true).when(this.oldcore.getSpyXWiki()).checkAccess(any(), any(), any());
    }

    @Test
    public void testImportWithHeterogeneousEncodingInFiles() throws Exception
    {
        String docTitle = "Un \u00e9t\u00e9 36";
        String docContent = "\u00e0\u00e7\u00e9\u00e8\u00c0\u00c7\u00c9\u00c8\u00ef\u00f6\u00eb\u00fc";

        XWikiDocument doc1 = new XWikiDocument(new DocumentReference("Wiki", "Main", "Document1"));
        doc1.setTitle(docTitle);
        doc1.setContent(docContent);

        XWikiDocument doc2 = new XWikiDocument(new DocumentReference("Wiki", "Main", "Document2"));
        doc2.setTitle(docTitle);
        doc2.setContent(docContent);

        XWikiDocument docs[] = { doc1, doc2 };

        this.pack.Import(this.createZipFile(docs, new String[] { "ISO-8859-1", "UTF-8" }, null),
            this.oldcore.getXWikiContext());

        assertEquals(2, this.pack.getFiles().size());
        assertEquals(this.pack.getFiles().get(0).getDoc().getTitle(),
            (this.pack.getFiles().get(1)).getDoc().getTitle());
        assertEquals(this.pack.getFiles().get(0).getDoc().getContent(),
            this.pack.getFiles().get(1).getDoc().getContent());
    }

    @Test
    public void testImportWithHeterogeneousEncodingInFilesUsingCommonsCompress() throws Exception
    {
        String docTitle = "Un \u00e9t\u00e9 36";
        String docContent = "\u00e0\u00e7\u00e9\u00e8\u00c0\u00c7\u00c9\u00c8\u00ef\u00f6\u00eb\u00fc";

        XWikiDocument doc1 = new XWikiDocument(new DocumentReference("Wiki", "Main", "Document1"));
        doc1.setTitle(docTitle);
        doc1.setContent(docContent);

        XWikiDocument doc2 = new XWikiDocument(new DocumentReference("Wiki", "Main", "Document2"));
        doc2.setTitle(docTitle);
        doc2.setContent(docContent);

        XWikiDocument docs[] = { doc1, doc2 };

        this.pack.Import(this.createZipFileUsingCommonsCompress(docs, new String[] { "ISO-8859-1", "UTF-8" }, null),
            this.oldcore.getXWikiContext());

        assertEquals(2, this.pack.getFiles().size());
        assertEquals(this.pack.getFiles().get(0).getDoc().getTitle(),
            (this.pack.getFiles().get(1)).getDoc().getTitle());
        assertEquals(this.pack.getFiles().get(0).getDoc().getContent(),
            this.pack.getFiles().get(1).getDoc().getContent());
    }

}
