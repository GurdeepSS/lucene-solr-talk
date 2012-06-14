package de.fhopf.lucene;

import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Searches on an index.
 *
 * Note: When used from a webapp this implementation is not as efficient as it should be. On each request it opens
 * a new IndexReader which can be quite costly. If you are interested in how to implement a SearchManager that takes
 * care of opening and updating IndexReaders have a look at Chapter 11 of Lucene in Action.
 */
public class Searcher {

    private Logger logger = LoggerFactory.getLogger(Searcher.class);

    private final Directory directory;

    public Searcher(Directory directory) {
        this.directory = directory;
    }

    public List<Document> searchCategory(String category) {
        Query query = new TermQuery(new Term("category", category));
        return search(query, null);
    }

    private List<Document> search(Query query, Filter filter) {
        IndexSearcher searcher = null;
        try {
            searcher = new IndexSearcher(IndexReader.open(directory));
            List<Document> result = new ArrayList<Document>();
            TopDocs topDocs = searcher.search(query, filter, 10);
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                result.add(doc);
            }
            return result;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } finally {
            if (searcher != null) {
                try {
                    searcher.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public List<Document> search(String query) throws ParseException {
        return search(query, null);
    }

    public List<Document> search(String query, String category) throws ParseException {
        QueryParser queryParser = new QueryParser(Version.LUCENE_36, "all", new GermanAnalyzer(Version.LUCENE_36));
        Query actualQuery = queryParser.parse(query);
        Filter filter = null;
        if (category != null && !category.trim().isEmpty()) {
            filter = new TermRangeFilter("category", category, category, true, true);
        }
        logger.info("Searching for {} with filter {}", query, filter);
        return search(actualQuery, filter);

    }

    public List<String> getAllCategories() {
        IndexReader reader = null;
        try {
            reader = IndexReader.open(directory);

            Utils.logTermDictionary(reader);

            TermEnum terms = reader.terms(new Term("category", ""));
            List<String> categories = new ArrayList<String>();
            if (terms.term() != null) {
                do {
                    Term term = terms.term();
                    if (!"category".equals(term.field())) {
                        break;
                    }
                    categories.add(term.text());
                } while (terms.next());
            }
            return categories;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            Utils.close(reader);
        }
    }

    public static void main(String[] args) throws IOException, ParseException {
        if (args.length != 2) {
            System.out.println(String.format("Usage: java %s <indexdir> <query>", Searcher.class.getCanonicalName()));
            System.exit(0);
        }

        Directory dir = FSDirectory.open(new File(args[0]));
        Searcher searcher = new Searcher(dir);
        List<Document> docs = searcher.search(args[1]);
        System.out.println(String.format("Found %d results", docs.size()));
        for (Document doc : docs) {
            System.out.println(String.format("%s", doc.get("title")));
        }
    }
}
