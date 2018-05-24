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
package org.xwiki.notifications.notifiers.internal.email;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xwiki.notifications.CompositeEvent;

/**
 * Sort composite events (wrapped by {@link SortedEvent}) so that it is easy to display a table of content in the email.
 *
 * @version $Id$
 * @since 9.11RC1
 */
public class EventsSorter
{
    private List<SortedEvent> sortedEvents = new ArrayList<>();

    /**
     * Add a new event to take into account.
     *
     * @param event event to add
     * @param html the HTML-rendered version of the event
     * @param plainText the plain text-rendered version of the event
     */
    public void add(CompositeEvent event, String html, String plainText)
    {
        sortedEvents.add(new SortedEvent(event, html, plainText));
    }

    /**
     * @return a map of sorted events, grouped by wiki
     */
    public Map<String, List<SortedEvent>> sort()
    {
        // Sort the top level events, that have no parent
        Collections.sort(sortedEvents, getSortedEventComparator());

        // Group sorted events that concern the same document
        groupEventsWithSameDocument();

        // Here we are going to store only events that have no parent
        List<SortedEvent> topLevelEvents = new ArrayList<>();
        // Group events so that children are stored in their parent
        for (SortedEvent sortedEvent : sortedEvents) {
            SortedEvent nearestParent = findNearestParent(sortedEvent);
            if (nearestParent != null) {
                nearestParent.addChild(sortedEvent);
            } else {
                topLevelEvents.add(sortedEvent);
            }
        }

        // Replace the inner list by the hierarchy
        this.sortedEvents = topLevelEvents;

        // Create a map of sorted events, grouped by wiki
        Map<String, List<SortedEvent>> sortedEventsByWikis = new HashMap<>();
        for (SortedEvent sortedEvent: sortedEvents) {
            String wiki = getWiki(sortedEvent);
            List<SortedEvent> list = getList(sortedEventsByWikis, wiki);
            list.add(sortedEvent);
        }
        return sortedEventsByWikis;
    }

    private Comparator<SortedEvent> getSortedEventComparator()
    {
        // Sort by document first (if document == null, go last)
        // Sort by date (more recent first) then, when document is the same
        return (e1, e2) -> {
            if (e1.getDocument() == null) {
                return e2.getDocument() == null ? 0 : 1;
            }
            if (e2.getDocument() == null) {
                return -1;
            }
            int documentSort = e1.getDocument().compareTo(e2.getDocument());
            if (documentSort == 0) {
                return e2.getDate().compareTo(e1.getDate());
            }
            return documentSort;
        };
    }

    private String getWiki(SortedEvent sortedEvent)
    {
        return sortedEvent.getEvent().getDocument() != null
                ? sortedEvent.getEvent().getDocument().getWikiReference().getName()
                : "";
    }

    private SortedEvent findNearestParent(SortedEvent sortedEvent)
    {
        if (sortedEvent.getEvent().getDocument() == null) {
            return null;
        }

        SortedEvent nearestParent = null;
        int nearestParentLevel = 0;

        for (SortedEvent possibleParent : sortedEvents) {
            if (possibleParent == sortedEvent) {
                continue;
            }
            if (possibleParent.isParent(sortedEvent)
                    && possibleParent.getEvent().getDocument().size() > nearestParentLevel) {
                nearestParent = possibleParent;
                nearestParentLevel = possibleParent.getEvent().getDocument().size();
            }
        }
        return nearestParent;
    }

    private List<SortedEvent> getList(Map<String, List<SortedEvent>> sortedEventsByWikis, String wiki)
    {
        List<SortedEvent> list = sortedEventsByWikis.get(wiki);
        if (list == null) {
            list = new ArrayList<>();
            sortedEventsByWikis.put(wiki, list);
        }
        return list;
    }

    private void groupEventsWithSameDocument()
    {
        for (int i = 0; i < sortedEvents.size(); ++i) {
            SortedEvent event = sortedEvents.get(i);

            if (event.getDocument() != null) {
                // We start at (i + 1) because the sortedEvents list is ordered
                int j = i + 1;
                while (j < sortedEvents.size()) {
                    SortedEvent otherEvent = sortedEvents.get(j);
                    if (event.getDocument().equals(otherEvent.getDocument())) {
                        event.addEventWithTheSameDocument(otherEvent);
                        sortedEvents.remove(j);
                    } else {
                        ++j;
                    }
                }
            }
        }
    }
}
