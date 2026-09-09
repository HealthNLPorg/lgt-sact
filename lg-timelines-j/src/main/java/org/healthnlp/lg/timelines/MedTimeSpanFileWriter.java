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
      name = "MedTimeSpanFileWriter",
      description = "Writes mention-level medication temporal relations and spans in a table file.",
      role = PipeBitInfo.Role.WRITER,
      dependencies = { DOCUMENT_ID, IDENTIFIED_ANNOTATION, TIMEX, TEMPORAL_RELATION },
      usables = { DOCUMENT_ID_PREFIX }
)
public class MedTimeSpanFileWriter extends AbstractTableFileWriter {
   // If you do not need to utilize the entire cas, or need more than the doc cas, consider AbstractFileWriter<T>.
   static private final Logger LOGGER = LoggerFactory.getLogger( "MedTimeSpanFileWriter" );


   static private final List<String> HEADER
         = Arrays.asList( " Medication Normal ", " Medication Text ", " Medication Span ",
         " Temporal Relation ",
         " Normalized Time ", " Time Text ", " Time Span ", " Time Type ", " TimeNorm ISO "  );


   /**
    * {@inheritDoc}
    */
   @Override
   protected File getOutputFile( String outputDir, String documentId, String fileName) {
      return new File(outputDir, documentId + "_medTimeSpans." + getTableType().name().toLowerCase());
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
    * {@inheritDoc}
    */
   @Override
   protected List<List<String>> createDataRows( final JCas jCas ) {
      final Collection<TemporalRelation> tRels = JCasUtil.select( jCas, TemporalRelation.class );
      if ( tRels == null || tRels.isEmpty() ) {
         return Collections.emptyList();
      }
      // Sort rows by Normalized Med, followed by Relation Type
      final List<TemporalRelation> tlinks = tRels.stream()
                                                 .sorted( Comparator.comparing( TimeNormalUtil.getMed )
                                                                    .thenComparing( TemporalRelation::getCategory ) )
                                                 .toList();
      final List<List<String>> rows = new ArrayList<>();
      for ( TemporalRelation tlink : tlinks ) {
         final String medNormal = TimeNormalUtil.getMed.apply( tlink );
         final String relation = tlink.getCategory();
         final List<IdentifiedAnnotation> meds = getMentions( tlink.getArg1() );
         final List<TimeNormal> typeTimeNormals = TimeNormalUtil.createTimeNormals( tlink );
         for ( IdentifiedAnnotation med : meds ) {
            final String medText = med.getCoveredText();
            final String medSpan = med.getBegin()+","+med.getEnd();
            for ( TimeNormal timeNormal : typeTimeNormals ) {
               final List<String> row = Arrays.asList( medNormal, medText, medSpan, relation,
                     timeNormal.timeNormal(), timeNormal.timex(), timeNormal.timexSpan(),
                     timeNormal.timeType(), timeNormal.iso() );
               if ( !rows.contains( row ) ) {
                  rows.add( row );
               }
            }
         }
      }
      return rows;
   }


}
