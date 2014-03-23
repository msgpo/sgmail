package com.subgraph.sgmail.search.impl;

import com.subgraph.sgmail.messages.StoredMessage;
import com.subgraph.sgmail.search.MessageSearchIndex;
import com.subgraph.sgmail.search.SearchResult;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MessageSearchIndexImpl implements MessageSearchIndex {
    private final static Logger logger = Logger.getLogger(MessageSearchIndexImpl.class.getName());

    private final File indexDirectory;
    private final Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_47);
    private final QueryParser queryParser = new MultiFieldQueryParser(Version.LUCENE_47, new String[] {"body", "subject"}, analyzer);

    private MessageIndexWriter writer;
    private MessageIndexReader reader;
    private boolean isClosed;

    public MessageSearchIndexImpl(File indexDirectory) {
        this.indexDirectory = indexDirectory;
    }

    public void addMessage(StoredMessage message) throws IOException {
        getWriter().indexMessage(message);
    }

    public void removeMessage(StoredMessage message) {
        logger.warning("removeMessage called but not yet implemented");
    }

    public SearchResult search(String queryString) throws IOException {
        final IndexSearcher searcher = getReader().getIndexSearcher();
        final Query query = createQuery(queryString);
        return SearchResultImpl.runQuery(queryString, query, searcher);
    }

    private Query createQuery(String input) {
        try {
            return queryParser.parse(input);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        final BooleanQuery query = new BooleanQuery();
        query.add(new TermQuery(new Term("body", input.toLowerCase())), BooleanClause.Occur.SHOULD);
        query.add(new TermQuery(new Term("subject", input.toLowerCase())), BooleanClause.Occur.SHOULD);
        return query;
    }

    public void commit() {
        try {
            getWriter().commitIndex();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error committing search index: "+ e.getMessage(), e);
        }
    }

    public synchronized void close() {
        if(isClosed) {
            return;
        } else {
            isClosed = true;
        }
        if(writer != null) {
            writer.closeIndex();
        }
        if(reader != null) {
            reader.closeIndex();
        }
    }

    synchronized  MessageIndexReader getReader() throws IOException {
        if(isClosed) {
            throw new IOException("Index has been closed");
        }
        if(reader == null) {
            reader = new MessageIndexReader(getWriter().getIndexWriter());
        }
        return reader;
    }

    synchronized MessageIndexWriter getWriter() throws IOException {
        if(isClosed) {
            throw new IOException("Index has been closed");
        }
        if(writer == null) {
            writer = MessageIndexWriter.openIndexWriter(indexDirectory, false);
        }
        return writer;
    }


}