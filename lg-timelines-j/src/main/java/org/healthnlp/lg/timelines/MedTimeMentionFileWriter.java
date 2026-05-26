package org.healthnlp.lg.timelines;

import org.apache.ctakes.core.cc.AbstractTableFileWriter;
import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.typesystem.type.refsem.Element;
import org.apache.ctakes.typesystem.type.relation.TemporalRelation;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

import static org.apache.ctakes.core.pipeline.PipeBitInfo.TypeProduct.*;
import static org.healthnlp.lg.timelines.TimeNormalUtil.TimeNormal;


/**
 * Todo - refactor and 'merge' parts of AbstractPatientFileWriter (maybe interface that one)?
 * @author SPF , chip-nlp
 * @since {3/20/2026}
 */
@PipeBitInfo (
      name = "MedTimeMentionFileWriter",
      description = "Writes medication temporal relations in a table file.",
      role = PipeBitInfo.Role.WRITER,
      dependencies = { DOCUMENT_ID, IDENTIFIED_ANNOTATION, TIMEX, TEMPORAL_RELATION },
      usables = { DOCUMENT_ID_PREFIX }
)
public class MedTimeMentionFileWriter extends AbstractTableFileWriter {
   // If you do not need to utilize the entire cas, or need more than the doc cas, consider AbstractFileWriter<T>.
   static private final Logger LOGGER = LoggerFactory.getLogger( "MedTimeMentionFileWriter" );


   static private final List<String> HEADER
         = Arrays.asList( " Medication ", " Temporal Relation ", " TimeNorm ISO ", " Normalized Time ",
         " Temporal Expression ", " Snippet " );


   /**
    * {@inheritDoc}
    */
   @Override
   protected File getOutputFile( String outputDir, String documentId, String fileName) {
      return new File(outputDir, documentId + "_medTimeMentions." + getTableType().name().toLowerCase());
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected List<String> createHeaderRow( final JCas jCas ) {
      return HEADER;
   }

   /**
    *
    * @param element some Concept cTAKES element, e.g. Time or Date
    * @return annotations associated with the concept.
    */
   static private List<IdentifiedAnnotation> getMentions( final Element element ) {
      final FSArray<IdentifiedAnnotation> mentions =  element.getMentions();
      if ( mentions == null ) {
         return Collections.emptyList();
      }
      return mentions.stream()
                     .sorted( Comparator.comparing( IdentifiedAnnotation::getBegin )
                                        .thenComparing( IdentifiedAnnotation::getEnd ) )
                     .toList();
   }

   /**
    *
    * @param jCas ye olde ...
    * @param med annotation for medication.
    * @param time annotation for time.
    * @return the text between and including the two mentions.
    */
   static private String getSnippet( final JCas jCas, final IdentifiedAnnotation med, final IdentifiedAnnotation time ) {
      final int begin = Math.min( med.getBegin(), time.getBegin() );
      final int end = Math.max( med.getEnd(), time.getEnd() );
      return jCas.getDocumentText().substring( begin, end );
   }

   /**
    *
    * @param jCas ye olde ...
    * @param tlink temporal relation between medication and time.
    * @return MentionText records containing covered text and covered snippets.
    */
   static private List<MentionText> getMentionTexts( final JCas jCas, final TemporalRelation tlink ) {
      final List<IdentifiedAnnotation> meds = getMentions( tlink.getArg1() );
      final List<IdentifiedAnnotation> times = getMentions( tlink.getArg2() );
      final List<MentionText> mentionTexts = new ArrayList<>();
      for ( IdentifiedAnnotation med : meds ) {
         for ( IdentifiedAnnotation time : times ) {
            mentionTexts.add(
                  new MentionText( med.getCoveredText(), time.getCoveredText(), getSnippet( jCas, med, time ) ) );
         }
      }
      return mentionTexts;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected List<List<String>> createDataRows( final JCas jCas ) {
      final Collection<TemporalRelation> tRels = JCasUtil.select( jCas, TemporalRelation.class );
      if ( tRels == null || tRels.isEmpty() ) {
         return Collections.emptyList();
      }
      final List<TemporalRelation> tlinks = tRels.stream()
                                                 .sorted( Comparator.comparing(TimeNormalUtil.getMed )
                                                                    .thenComparing( TemporalRelation::getCategory ) )
                                                 .toList();
      final List<List<String>> rows = new ArrayList<>();
      for ( TemporalRelation tlink : tlinks ) {
         final String med = TimeNormalUtil.getMed.apply( tlink );
         final String relation = tlink.getCategory();
         final List<TimeNormal> typeTimeNormals = TimeNormalUtil.createTimeNormals( tlink );
         for ( TimeNormal timeNormal : typeTimeNormals ) {
            rows.add( Arrays.asList( med, relation, timeNormal.iso(), timeNormal.timeNormal(), timeNormal.timex(), "" ) );
            final List<MentionText> mentionTexts = getMentionTexts( jCas, tlink );
            mentionTexts.forEach( m -> rows.add( m.getRow() ) );
         }
      }
      return rows;
   }

   private record MentionText( String med, String time, String snippet ) {
      private List<String> getRow() {
         return Arrays.asList( med, "", "", "", time, snippet );
      }
   }


}
