/*
 Copyright 2008-2010 Gephi
 Authors : Mathieu Bastian <mathieu.bastian@gephi.org>
 Website : http://www.gephi.org

 This file is part of Gephi.

 DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.

 Copyright 2011 Gephi Consortium. All rights reserved.

 The contents of this file are subject to the terms of either the GNU
 General Public License Version 3 only ("GPL") or the Common
 Development and Distribution License("CDDL") (collectively, the
 "License"). You may not use this file except in compliance with the
 License. You can obtain a copy of the License at
 http://gephi.org/about/legal/license-notice/
 or /cddl-1.0.txt and /gpl-3.0.txt. See the License for the
 specific language governing permissions and limitations under the
 License.  When distributing the software, include this License Header
 Notice in each file and include the License files at
 /cddl-1.0.txt and /gpl-3.0.txt. If applicable, add the following below the
 License Header, with the fields enclosed by brackets [] replaced by
 your own identifying information:
 "Portions Copyrighted [year] [name of copyright owner]"

 If you wish your version of this file to be governed by only the CDDL
 or only the GPL Version 3, indicate your decision by adding
 "[Contributor] elects to include this software in this distribution
 under the [CDDL or GPL Version 3] license." If you do not indicate a
 single choice of license, a recipient has the option to distribute
 your version of this file under either the CDDL, the GPL Version 3 or
 to extend the choice of license to its licensees as provided above.
 However, if you add GPL Version 3 code and therefore, elected the GPL
 Version 3 license, then the option applies only if the new code is
 made subject to such option by the copyright holder.

 Contributor(s):

 Portions Copyrighted 2011 Gephi Consortium.
 */
package org.gephi.io.importer.plugin.file;

import java.awt.Color;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.util.ArrayList;
import org.gephi.io.importer.api.*;
import org.gephi.io.importer.spi.FileImporter;
import org.gephi.utils.longtask.spi.LongTask;
import org.gephi.utils.progress.Progress;
import org.gephi.utils.progress.ProgressTicket;
import org.openide.util.NbBundle;

//Inspired from infovis.graph.io;
//Original author Jean-Daniel Fekete
public class ImporterGML implements FileImporter, LongTask
{
    //Architecture
    private Reader reader;
    private ContainerLoader container;
    private Report report;
    private ProgressTicket progressTicket;
    private boolean cancel = false;

    private static final char STRING_DELIMITER = '"';
    
    private static final char LIST_BEGIN = '[';

    private static final char LIST_END = ']';
    
    private static final String NODE_ID = "id";
    
    private static final String NODE_LABEL = "label";
    
    private static final String EDGE_SOURCE = "source";

    private static final String EDGE_TARGET = "target";

    private static final String EDGE_WEIGHT = "weight";
  
    private static final String EDGE_VALUE = "value";

    private static final String EDGE_LABEL = "label";
    
    private static final String EDGE_TYPE_DIRECTED = "directed";
    
    @Override
    public boolean execute(ContainerLoader container) {
        this.container = container;
        this.report = new Report();
        LineNumberReader lineReader = ImportUtils.getTextReader(reader);
        try {
            importData(lineReader);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                lineReader.close();
            } catch (IOException ex) {
            }
        }
        return !cancel;
    }

    private void importData(LineNumberReader reader) throws Exception {
        Progress.start(progressTicket);

        ArrayList<Object> list = parseList(reader);

        boolean ret = false;
        for (int i = 0; i < list.size(); i++) {
            if ("graph".equals(list.get(i)) && list.size() >= i + 2 && list.get(i + 1) instanceof ArrayList) {
                ret = parseGraph((ArrayList<?>) list.get(i + 1));
            }
        }
        if (!ret) {
            report.logIssue(new Issue(NbBundle.getMessage(ImporterGML.class, "importerGML_error_badparsing"), Issue.Level.SEVERE));
        }

        Progress.finish(progressTicket);
    }

    private ArrayList<Object> parseList(LineNumberReader reader) throws IOException {

        ArrayList<Object> list = new ArrayList<>();
        char t;
        boolean readingString = false;
        StringBuilder stringBuffer = new StringBuilder();

        while (reader.ready()) {
            t = (char) reader.read();
            if (readingString) {
                if (t == STRING_DELIMITER) {
                    list.add(stringBuffer.toString());
                    stringBuffer.setLength(0);
                    readingString = false;
                } else {
                    stringBuffer.append(t);
                }
            } else {
                switch (t) {
                    case LIST_BEGIN:
                        list.add(parseList(reader));
                        break;
                    case LIST_END:
                        return list;
                    case STRING_DELIMITER:
                        readingString = true;
                        break;
                    case ' ':
                    case '\t':
                    case '\n':
                        if (stringBuffer.length() == 0) {
                            try {
                            	Integer intValue = Integer.valueOf(stringBuffer.toString());
                            	list.add(intValue);
                            } catch (NumberFormatException eInt) {
	                        	try {
	                                Double doubleValue = Double.valueOf(stringBuffer.toString());
	                                list.add(doubleValue);
	                            } catch (NumberFormatException eDouble) {
	                            	list.add(stringBuffer.toString()); // Actually add an empty string
	                            }
                            }
                            stringBuffer.setLength(0);
                        }
                        break;
                    default:
                        stringBuffer.append(t);
                        break;
                }
            }
        }
        return list;
    }

    private boolean parseGraph(ArrayList<?> list) {
        if ((list.size() & 1) != 0) {
            return false;
        }
        Progress.switchToDeterminate(progressTicket, list.size());

        boolean ret = true;
        for (int i = 0; i < list.size(); i += 2) {
            Object key = list.get(i);
            Object value = list.get(i + 1);
            if ("node".equals(key)) {
                ret = parseNode((ArrayList<?>) value);
            } else if ("edge".equals(key)) {
                ret = parseEdge((ArrayList<?>) value);
            } else if (EDGE_TYPE_DIRECTED.equalsIgnoreCase((String) key)) {
                if (value instanceof Integer) {
                    EdgeDirectionDefault edgeDefault = ((Integer) value) == 1 ? EdgeDirectionDefault.DIRECTED : EdgeDirectionDefault.UNDIRECTED;
                    container.setEdgeDefault(edgeDefault);
		} else if (value instanceof Double) {
                    EdgeDirectionDefault edgeDefault = ((Double) value) == 1.0 ? EdgeDirectionDefault.DIRECTED : EdgeDirectionDefault.UNDIRECTED;
                    container.setEdgeDefault(edgeDefault);
                } else {
                    report.logIssue(new Issue(NbBundle.getMessage(ImporterGML.class, "importerGML_error_directedgraphparse"), Issue.Level.WARNING));
                }
            } else {
            }
            if (!ret) {
                break;
            }
            if (cancel) {
                break;
            }
            Progress.progress(progressTicket);
        }
        return ret;
    }

    private boolean parseNode(ArrayList<?> list) {
        String id = null;
        String label = null;
        for (int i = 0; i < list.size() && (id == null || label == null); i += 2) {
            String key = (String) list.get(i);
            Object value = list.get(i + 1);
            if (id == null && NODE_ID.equalsIgnoreCase(key)) {
                id = value.toString();
            } else if (label == null && NODE_LABEL.equalsIgnoreCase(key)) {
                label = value.toString();
            }
        }
        NodeDraft node;
        if (id != null) {
            node = container.factory().newNodeDraft(id);
        } else {
            node = container.factory().newNodeDraft();
            report.logIssue(new Issue(NbBundle.getMessage(ImporterGML.class, "importerGML_error_nodeidmissing"), Issue.Level.WARNING));
        }
        
        if (label != null) {
            node.setLabel(label);
        }

        boolean ret = addNodeAttributes(node, "", list);
        container.addNode(node);
        return ret;
    }

    private boolean addNodeAttributes(NodeDraft node, String prefix, ArrayList<?> list) {
        boolean ret = true;
        for (int i = 0; i < list.size(); i += 2) {
            String key = (String) list.get(i);
            Object value = list.get(i + 1);
            if (NODE_ID.equalsIgnoreCase(key) || NODE_LABEL.equalsIgnoreCase(key)) {
                continue; // already parsed
            }
            if (value instanceof ArrayList) {
                // keep the  hierarchy
                ret = addNodeAttributes(node, prefix + "." + key, (ArrayList<?>) value);
                if (!ret) {
                    break;
                }
            } else if ("x".equalsIgnoreCase(key) && value instanceof Number) {
                node.setX(((Number) value).floatValue());
            } else if ("y".equalsIgnoreCase(key) && value instanceof Number) {
                node.setY(((Number) value).floatValue());
            } else if ("z".equalsIgnoreCase(key) && value instanceof Number) {
                node.setZ(((Number) value).floatValue());
            } else if ("w".equalsIgnoreCase(key) && value instanceof Number) {
                node.setSize(((Number) value).floatValue());
            } else if ("h".equalsIgnoreCase(key)) {
            } else if ("d".equalsIgnoreCase(key)) {
            } else if ("fill".equalsIgnoreCase(key)) {
                if (value instanceof String) {
                    node.setColor((String) value);
                } else if (value instanceof Number) {
                    node.setColor(new Color(((Number) value).intValue()));
                }
            } else {
                node.setValue(key, value);
            }
        }
        return ret;
    }

    private boolean parseEdge(ArrayList<?> list) {
        EdgeDraft edgeDraft = container.factory().newEdgeDraft();
        for (int i = 0; i < list.size(); i += 2) {
            String key = (String) list.get(i);
            Object value = list.get(i + 1);
            if (EDGE_SOURCE.equalsIgnoreCase(key)) {
                NodeDraft source = container.getNode(value.toString());
                edgeDraft.setSource(source);
            } else if (EDGE_TARGET.equalsIgnoreCase(key)) {
                NodeDraft target = container.getNode(value.toString());
                edgeDraft.setTarget(target);
            } else if (EDGE_VALUE.equalsIgnoreCase(key) || EDGE_WEIGHT.equalsIgnoreCase(key)) {
                if (value instanceof Double) {
                    edgeDraft.setWeight(((Double) value));
                }
            } else if (EDGE_LABEL.equalsIgnoreCase(key)) {
                edgeDraft.setLabel(value.toString());
            }
        }
        boolean ret = addEdgeAttributes(edgeDraft, "", list);
        container.addEdge(edgeDraft);
        return ret;
    }

    private boolean addEdgeAttributes(EdgeDraft edge, String prefix, ArrayList<?> list) {
        boolean ret = true;
        for (int i = 0; i < list.size(); i += 2) {
            String key = (String) list.get(i);
            Object value = list.get(i + 1);
            if (EDGE_SOURCE.equalsIgnoreCase(key) || EDGE_TARGET.equalsIgnoreCase(key) || EDGE_VALUE.equalsIgnoreCase(key) || EDGE_WEIGHT.equalsIgnoreCase(key) || EDGE_LABEL.equalsIgnoreCase(key)) {
                continue; // already parsed
            }
            if (value instanceof ArrayList) {
                // keep the hierarchy
                ret = addEdgeAttributes(edge, prefix + "." + key, (ArrayList<?>) value);
                if (!ret) {
                    break;
                }
            } else if (EDGE_TYPE_DIRECTED.equalsIgnoreCase(key)) {
                if (value instanceof Integer) {
                    EdgeDirection type = ((Integer) value) == 1 ? EdgeDirection.DIRECTED : EdgeDirection.UNDIRECTED;
                    edge.setType(type);
                } else {
                    report.logIssue(new Issue(NbBundle.getMessage(ImporterGML.class, "importerGML_error_directedparse", edge.toString()), Issue.Level.WARNING));
                }
            } else if ("fill".equalsIgnoreCase(key)) {
                if (value instanceof String) {
                    edge.setColor((String) value);
                } else if (value instanceof Number) {
                    edge.setColor(new Color(((Number) value).intValue()));
                }
            } else {
                edge.setValue(key, value);
            }
        }
        return ret;
    }

    @Override
    public void setReader(Reader reader) {
        this.reader = reader;
    }

    @Override
    public ContainerLoader getContainer() {
        return container;
    }

    @Override
    public Report getReport() {
        return report;
    }

    @Override
    public boolean cancel() {
        cancel = true;
        return true;
    }

    @Override
    public void setProgressTicket(ProgressTicket progressTicket) {
        this.progressTicket = progressTicket;
    }
}
