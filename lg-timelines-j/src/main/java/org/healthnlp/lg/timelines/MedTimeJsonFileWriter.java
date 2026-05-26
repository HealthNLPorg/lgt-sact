package org.healthnlp.lg.timelines;

import org.apache.ctakes.core.cc.AbstractFileWriter;
import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.core.util.AeParamUtil;
import org.apache.ctakes.core.util.StringUtil;
import org.apache.ctakes.core.util.doc.SourceMetadataUtil;
import org.apache.ctakes.typesystem.type.relation.TemporalRelation;
import org.apache.ctakes.typesystem.type.structured.Corpus;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.ctakes.core.pipeline.PipeBitInfo.TypeProduct.*;

/**
 * @author SPF , chip-nlp
 * @since {4/20/2026}
 */
@PipeBitInfo (
      name = "MedTimeJsonFileWriter",
      description = "Writes medication temporal relations in a json file.",
      role = PipeBitInfo.Role.WRITER,
      dependencies = { DOCUMENT_ID, IDENTIFIED_ANNOTATION, TIMEX, TEMPORAL_RELATION },
      usables = { DOCUMENT_ID_PREFIX }
)
public class MedTimeJsonFileWriter extends AbstractFileWriter<Collection<MedTimeJsonFileWriter.MedTLink>> {

   static private final Logger LOGGER = LoggerFactory.getLogger( "MedTimeJsonFileWriter" );

   static public final String WRITE_TIME_PARAM = "WriteTime";
   static public final String WRITE_TIME_DESC = "YES to write hours and minutes HH:MM after a date.";
   @ConfigurationParameter (
         name = WRITE_TIME_PARAM,
         description = WRITE_TIME_DESC,
         defaultValue = "no",
         mandatory = false
   )
   private String _writeTimeVal;

   static public final String HUMAN_READABLE_PARAM = "HumanReadable";
   static public final String HUMAN_READABLE_DESC = "YES for human-readable YYYY-MM-DD output, MIX for that plus ISO weeks.";
   @ConfigurationParameter (
         name = HUMAN_READABLE_PARAM,
         description = HUMAN_READABLE_DESC,
         defaultValue = "mix",
         mandatory = false
   )
   private String _humanReadableVal;

   private boolean _writeTime;
   private boolean _humanReadable;
   private boolean _mixedReadable;

   static private final String CORPUS_NOT_SET = "Corpus not set";
   private String _corpusName = CORPUS_NOT_SET;
   private String _currentPid = "";
   // Collection of med TLinks for current patient.
   private final Collection<MedTLink> _medTlinks = new HashSet<>();
   private boolean _isFirstWrite = true;
   private boolean _isLastWrite = false;


   /**
    * {@inheritDoc}
    */
   @Override
   public void initialize( UimaContext context) throws ResourceInitializationException {
      super.initialize( context );
      _writeTime = AeParamUtil.isTrue( _writeTimeVal );
      _humanReadable = AeParamUtil.isTrue( _humanReadableVal );
      _mixedReadable = _humanReadableVal.toUpperCase().startsWith( "MIX" );
   }

   /**
    * Add medication data from the cas to table rows.
    * {@inheritDoc}
    */
   @Override
   public void process( final JCas jCas ) throws AnalysisEngineProcessException {
      if ( _corpusName.equals( CORPUS_NOT_SET ) ) {
         final Collection<Corpus> corpus = JCasUtil.select( jCas, Corpus.class );
         if ( corpus != null && !corpus.isEmpty() ) {
            _corpusName = corpus.stream()
                                .filter( Objects::nonNull )
                                .map( Corpus::getCorpusName )
                                .filter( Objects::nonNull )
                                .filter( c -> !c.isBlank() )
                                .findFirst()
                                .orElse( CORPUS_NOT_SET );
         }
      }
      final String pid = SourceMetadataUtil.getPatientIdentifier( jCas );
      if ( _currentPid.isBlank() ) {
         _currentPid = pid;
      }
      if ( !_currentPid.equals( pid ) ) {
         try {
            writeFile( getData(), "", "", "" );
         } catch ( IOException ioE ) {
            throw new AnalysisEngineProcessException( ioE );
         }
         _medTlinks.clear();
      }
      createData( jCas );
      _currentPid = pid;
   }

   /**
    * Write the json to file.
    * {@inheritDoc}
    */
   @Override
   public void collectionProcessComplete() throws AnalysisEngineProcessException {
      // The last patient doc med tlinks haven't been written yet.
      _isLastWrite = true;
      try {
         writeFile( getData(), "", "", "" );
      } catch ( IOException ioE ) {
         throw new AnalysisEngineProcessException( ioE );
      }
   }

   /**
    * Does nothing, not used.
    * @param jCas -
    */
   @Override
   protected void createData( final JCas jCas ) {
      final Collection<TemporalRelation> tlinks = JCasUtil.select( jCas, TemporalRelation.class );
      final Collection<MedTLink> medTLinks = new HashSet<>();
      if ( tlinks == null || tlinks.isEmpty() ) {
         return;
      }
      for ( TemporalRelation tlink : tlinks ) {
         final String med = TimeNormalUtil.getMed.apply( tlink );
         final String relation = tlink.getCategory();
         TimeNormalUtil.createTimeNormals( tlink ).stream()
                       .filter( t ->  t.timeType().equals( TimeNormalUtil.INSTANT ) )
                       .map( this::toInstantText )
                       .map( t -> new MedTLink( med, relation, t ) )
                       .forEach( medTLinks::add );
      }
      _medTlinks.addAll( medTLinks );
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected Collection<MedTLink> getData() {
      final List<MedTLink> medTLinks = new ArrayList<>( _medTlinks );
      final Collection<MedTLink> removals = new HashSet<>();
      final Collection<MedTLink> newContains = new HashSet<>();
      for ( int i=0; i<medTLinks.size()-1; i++ ) {
         final MedTLink iLink = medTLinks.get( i );
         final String iMed = iLink.medText;
         final String iRelation = iLink.relation.toLowerCase();
         final String iTimeNormal = iLink.timeNormal;
         for ( int j=i+1; j<medTLinks.size(); j++ ) {
            final MedTLink jLink = medTLinks.get( j );
            if ( iMed.equalsIgnoreCase( jLink.medText ) && iTimeNormal.equalsIgnoreCase( jLink.timeNormal ) ) {
               final String jRelation = jLink.relation.toLowerCase();
               if ( (iRelation.startsWith( "begin" ) || iRelation.startsWith( "ends" ))
                     && jRelation.startsWith( "contains" ) ) {
                  removals.add( jLink );
               } else if ( (jRelation.startsWith( "begin" ) || jRelation.startsWith( "ends" ))
                     && iRelation.startsWith( "contains" ) ) {
                  removals.add( iLink );
               } else if ( (iRelation.startsWith( "begin" ) && jRelation.startsWith( "ends" ))
                     || (jRelation.startsWith( "begin" ) && iRelation.startsWith( "ends" )) ) {
                  removals.add( iLink );
                  removals.add( jLink );
                  newContains.add( new MedTLink( iMed, "contains-1", iTimeNormal ) );
               }
            }
         }
      }
      _medTlinks.removeAll( removals );
      _medTlinks.addAll( newContains );
      return _medTlinks;
   }

   /**
    * Does nothing, not used.
    * @param jsonObject -
    */
   @Override
   protected void writeComplete( final Collection<MedTLink> jsonObject ) {
   }

   /**
    * Returns the root output directory plus any subdirectory set using the "SubDirectory" parameter.
    * {@inheritDoc}
    */
   @Override
   protected String getOutputDirectory(JCas jcas, String rootPath, String documentId) {
      String subDirectory = getSimpleSubDirectory();
      if (subDirectory != null && !subDirectory.isEmpty()) {
         File outputDir = new File(rootPath + "/" + subDirectory);
         outputDir.mkdirs();
         return outputDir.getPath();
      } else {
         return rootPath;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeFile( final Collection<MedTLink> medTlinks,
                          final String outputDir,
                          final String documentId,
                          final String fileName ) throws IOException {
      final File file = new File( getOutputDirectory( null, getRootDirectory(), null ),
            _corpusName + "_medTlinks.json" );
      LOGGER.info( "Writing Corpus Med TLinks to {} ...", file.getPath() );
      try ( Writer writer = new BufferedWriter( new FileWriter( file, !_isFirstWrite ) ) ) {
         if ( _isFirstWrite ) {
            writer.write( "{\n" );
            _isFirstWrite = false;
         }
         writer.write( "  \"" + _currentPid + "\": [\n" );
         final String array = medTlinks.stream()
                                       .sorted( Comparator.comparing( MedTLink::medText )
                                                          .thenComparing( MedTLink::relation )
                                                          .thenComparing( MedTLink::timeNormal ) )
                                       .map( MedTLink::toString )
                                       .collect( Collectors.joining(",\n" ) );
         if ( _isLastWrite ) {
            writer.write( array + "\n  ]\n}\n" );
         } else {
            // need a comma between patients.
            writer.write( array + "\n  ],\n" );
         }
      } catch ( IOException ioE ) {
         LOGGER.error( "Could not write json file {}", file.getPath() );
         LOGGER.error( ioE.getMessage() );
      }
   }

   /**
    *
    * @param timeNormal Record containing all information a bout a timex and its normalization.
    * @return the human-readable normalization or its ISO equivalent.
    */
   private String toInstantText( final TimeNormalUtil.TimeNormal timeNormal ) {
      if ( _mixedReadable  ) {
         return timeNormal.iso().contains( "T" ) ? toHumanInstant( timeNormal ) : timeNormal.iso();
      }
      return _humanReadable ? toHumanInstant( timeNormal ) : timeNormal.iso();
   }

   /**
    *
    * @param timeNormal Record containing all information a bout a timex and its normalization.
    * @return the human-readable representation of the normalization.
    */
   private String toHumanInstant( final TimeNormalUtil.TimeNormal timeNormal ) {
      return _writeTime ? timeNormal.timeNormal() : StringUtil.fastSplit( timeNormal.timeNormal(), ' ' )[0];
   }

   /**
    * Record with hashcode and equals override so records with the same contents are equal.
    * @param medText
    * @param relation
    * @param timeNormal
    */
   public record MedTLink( String medText, String relation, String timeNormal ) {
      public String toString() {
         return "    [\"" + medText + "\", \"" + relation + "\", \"" + timeNormal + "\"]";
      }
      public int hashCode() {
         return toString().toUpperCase().hashCode();
      }
      public boolean equals( final Object other ) {
         return other instanceof MedTLink && toString().equalsIgnoreCase( other.toString() );
      }
   }

}
