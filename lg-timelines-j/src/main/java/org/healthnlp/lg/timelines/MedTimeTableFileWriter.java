package org.healthnlp.lg.timelines;

import org.apache.ctakes.core.cc.AbstractTableFileWriter;
import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.typesystem.type.relation.TemporalRelation;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
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
      name = "MedTimeTableFileWriter",
      description = "Writes medication temporal relations in a table file.",
      role = PipeBitInfo.Role.WRITER,
      dependencies = { DOCUMENT_ID, IDENTIFIED_ANNOTATION, TIMEX, TEMPORAL_RELATION },
      usables = { DOCUMENT_ID_PREFIX }
)
public class MedTimeTableFileWriter extends AbstractTableFileWriter {
   // If you do not need to utilize the entire cas, or need more than the doc cas, consider AbstractFileWriter<T>.
   static private final Logger LOGGER = LoggerFactory.getLogger( "MedTimeTableFileWriter" );


   static private final List<String> HEADER
         = Arrays.asList( " Medication ", " Medication Text ",
         " Temporal Relation ", " Time Type ", " TimeNorm ISO ", " Normalized Time ", " Temporal Expression " );

   /**
    * {@inheritDoc}
    */
   @Override
   protected File getOutputFile( String outputDir, String documentId, String fileName) {
      return new File(outputDir, documentId + "_medTimes." + getTableType().name().toLowerCase());
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected List<String> createHeaderRow( final JCas jCas ) {
      return HEADER;
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
      final List<MedTLink> medTLinks = new ArrayList<>( HEADER.size() );
      for ( TemporalRelation tlink : tlinks ) {
         final String med = TimeNormalUtil.getMed.apply( tlink );
         final String relation = tlink.getCategory();
         final List<TimeNormal> typeTimeNormals = TimeNormalUtil.createTimeNormals( tlink );
         for ( TimeNormal timeNormal : typeTimeNormals ) {
            medTLinks.add(
                  new MedTLink( "-- SACT --", med, relation, timeNormal ) );
         }
      }
      return medTLinks.stream().map( MedTLink::getRow ).toList();
   }


   private record MedTLink( String med, String medText, String relation, String timeType, String iso,
                            String timeNormal, String timex ) {
      private MedTLink( String med, String medText, String relation, TimeNormal timeNormal ) {
         this( med, medText, relation, timeNormal.timeType(), timeNormal.iso(), timeNormal.timeNormal(), timeNormal.timex() );
      }
      private List<String> getRow() {
         return Arrays.asList( med, medText, relation, timeType, iso, timeNormal, timex );
      }
   }


}
