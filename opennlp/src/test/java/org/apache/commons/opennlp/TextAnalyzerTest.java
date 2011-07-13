/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.commons.opennlp;

import static junit.framework.Assert.*;

import java.util.Iterator;
import java.util.List;

import org.apache.stanbol.commons.opennlp.OpenNLP;
import org.apache.stanbol.commons.opennlp.TextAnalyzer;
import org.apache.stanbol.commons.opennlp.TextAnalyzer.AnalysedText;
import org.apache.stanbol.commons.opennlp.TextAnalyzer.AnalysedText.Chunk;
import org.apache.stanbol.commons.opennlp.TextAnalyzer.AnalysedText.Token;
import org.junit.BeforeClass;
import org.junit.Test;

public class TextAnalyzerTest {

    public static final String SINGLE_SENTENCE = "Dr Patrick Marshall (1869 - November 1950) was a"
        + " geologist who lived in New Zealand and worked at the University of Otago.";
    
    public static final String[] SINGLE_SENTENCE_TOKENS = new String[]{
        "Dr", "Patrick", "Marshall", "(", "1869", "-", "November", "1950", 
        ")", "was", "a", "geologist", "who", "lived", "in", "New", "Zealand", 
        "and", "worked", "at", "the", "University", "of", "Otago", "."
    };

    public static final String MULTI_SENTENCES = "The life of Patrick Marshall.\n\n"
        + "Dr Patrick Marshall (1869 - November 1950) was a geologist who lived "
        + "in New Zealand and worked at the University of Otago. "
        + "This is another unrelated sentence without any name.";

    private static String[][] MULTIPLE_SENTENCE_TOKENS = new String[3][];
    static {
        MULTIPLE_SENTENCE_TOKENS[0] = new String[]{
            "The","life","of","Patrick","Marshall","."
        };
        MULTIPLE_SENTENCE_TOKENS[1] = SINGLE_SENTENCE_TOKENS;
        MULTIPLE_SENTENCE_TOKENS[2] = new String[]{
            "This","is","another","unrelated","sentence","without","any","name","."
        };
    }

    
    private static OpenNLP openNLP;    
    @BeforeClass
    public static void init(){
        openNLP = new OpenNLP(new ClasspathDataFileProvider("DUMMY"));
    }


    @Test
    public void testSingleSentenceDefaultConfig(){
        TextAnalyzer analyzer = new TextAnalyzer(openNLP, "en");
        AnalysedText analysed = analyzer.analyseSentence(SINGLE_SENTENCE);
        assertNotNull(analysed);
        //check the default config
        assertTrue(analyzer.isSimpleTokenizerForced());
        assertTrue(analyzer.isPosTaggerEnable());
        assertTrue(analyzer.isPosTypeChunkerEnabled());
        assertTrue(analyzer.isChunkerEnabled());
        assertTrue(analyzer.isPosTypeChunkerForced());
        checkSingleSentence(analysed,SINGLE_SENTENCE_TOKENS,true,true);
    }
    @Test
    public void testSingleSentenceChunkerConfig(){
        TextAnalyzer analyzer = new TextAnalyzer(openNLP, "en");
        analyzer.forcePosTypeChunker(false);
        AnalysedText analysed = analyzer.analyseSentence(SINGLE_SENTENCE);
        assertNotNull(analysed);
        //check the default config
        assertTrue(analyzer.isSimpleTokenizerForced());
        assertTrue(analyzer.isPosTaggerEnable());
        assertTrue(analyzer.isChunkerEnabled());
        assertTrue(analyzer.isPosTypeChunkerEnabled());
        assertFalse(analyzer.isPosTypeChunkerForced());
        checkSingleSentence(analysed,SINGLE_SENTENCE_TOKENS,true,true);
    }
    @Test
    public void testSingleSentenceNoChunkerConfig(){
        TextAnalyzer analyzer = new TextAnalyzer(openNLP, "en");
        analyzer.enableChunker(false);
        AnalysedText analysed = analyzer.analyseSentence(SINGLE_SENTENCE);
        assertNotNull(analysed);
        //check the default config
        assertTrue(analyzer.isSimpleTokenizerForced());
        assertTrue(analyzer.isPosTaggerEnable());
        assertFalse(analyzer.isChunkerEnabled());
        assertTrue(analyzer.isPosTypeChunkerEnabled());
        assertTrue(analyzer.isPosTypeChunkerForced());
        checkSingleSentence(analysed,SINGLE_SENTENCE_TOKENS,true,false);
    }
    @Test
    public void testSingleSentenceNoChunkerNoPosConfig(){
        TextAnalyzer analyzer = new TextAnalyzer(openNLP, "en");
        analyzer.enablePosTagger(false);
        analyzer.enableChunker(true);//must be ignored for Chunks if no Pos
        AnalysedText analysed = analyzer.analyseSentence(SINGLE_SENTENCE);
        assertNotNull(analysed);
        //check the default config
        assertTrue(analyzer.isSimpleTokenizerForced());
        assertFalse(analyzer.isPosTaggerEnable());
        assertTrue(analyzer.isChunkerEnabled());
        assertTrue(analyzer.isPosTypeChunkerEnabled());
        assertTrue(analyzer.isPosTypeChunkerForced());
        checkSingleSentence(analysed,SINGLE_SENTENCE_TOKENS,false,false);
    }

    @Test
    public void testMultipleSentenceDefaultConfig(){
        TextAnalyzer analyzer = new TextAnalyzer(openNLP, "en");
        Iterator<AnalysedText> analysedSentences = analyzer.analyse(MULTI_SENTENCES);
        assertNotNull(analysedSentences);
        int sentenceCount = 0;
        while(analysedSentences.hasNext()){
            AnalysedText analysed = analysedSentences.next();
            checkSingleSentence(analysed, MULTIPLE_SENTENCE_TOKENS[sentenceCount], true, true);
            sentenceCount++;
        }
        assertTrue(sentenceCount == 3);
    }
    
    /**
     * @param tokens
     */
    private void checkSingleSentence(AnalysedText analysed, String[] expectedTokens,
                                     boolean posTaggerActive, boolean chunkerActive) {
        List<Token> tokens = analysed.getTokens();
        assertNotNull(tokens != null);
        assertTrue(tokens.size() == expectedTokens.length);
        int i=0;
        int lastEnd = -1;
        for(Token token : tokens){
            assertEquals(expectedTokens[i], token.getText());
            if(posTaggerActive){
                assertNotNull(token.getPosTag());
                assertTrue(token.getPosProbability() > 0);
            } else {
                assertNull(token.getPosTag());
                assertTrue(token.getPosProbability() < 0);
            }
            assertTrue(token.getStart() >= lastEnd);
            assertTrue(token.getEnd() > token.getStart());
            lastEnd = token.getEnd();
            i++;
        }
        List<Chunk> chunks = analysed.getChunks();
        if(chunkerActive){
            assertNotNull(chunks);
            i=0;
            lastEnd = -1;
            for(Chunk chunk : chunks){
                assertTrue(chunk.getStart() >= lastEnd);
                assertTrue(chunk.getEnd() >= chunk.getStart());
                assertTrue(chunk.getEnd() < expectedTokens.length);
                lastEnd = chunk.getEnd();
                i++;
                String chunkText = chunk.getText();
                assertTrue(analysed.getText().indexOf(chunkText) >= 0);
                for(int ct = chunk.getStart();ct <= chunk.getEnd();ct++){
                    assertTrue(chunkText.indexOf(tokens.get(ct).getText()) >= 0);;
                }
            }
        } else {
            assertNull(chunks);
        }
    }
    
}
