Java module for langgraph-timelines project,
A LangGraph-based pipeline for extracting 
and normalizing temporal information from clinical/patient notes using fine-tuned LLMs.

Contains Java files used to normalize temporal expressions in tlinks and write output files.

**PittHeaderParser**  
Obtains document creation time from University of Pittsburgh document headers. 

**MedTimeJsonFileWriter**  
Writes a file named after the corpus, with the suffix _medTlinks.json.  
The file contains tlinks written per-patient.  
Accepts standard [Apache cTAKES](https://github.com/apache/ctakes) FileWriter parameters plus two more:  

|Parameter|Description|Class| Required | Default |
|---|---|:---:|:--------:|---------|
|OutputDirectory|Directory for all output files.|File|   Yes    |         |
|SubDirectory|SubDirectory for files.|String|    No    |         |
|WriteTime|YES to write hours and minutes HH:MM after a date.|String|    No    | No      |
|HumanReadable|YES for human-readable YYYY-MM-DD output, MIX for that plus ISO weeks.|String|    No    | Mix     |


**MedTimeTableFileWriter**  
Writes a file named after the document, with the suffix _medTimes.  
Accepts standard [Apache cTAKES](https://github.com/apache/ctakes) TableFileWriter parameters:  

|Parameter|Description|Class|Required| Default |
|---|---|:---:|:---:|---------|
|OutputDirectory|Directory for all output files.|File|Yes|         |
|SubDirectory|SubDirectory for files.|String|No|         |
|TableType|Type of Table to write to File. Possible values are: BSV, CSV, HTML, TAB|String|No| bsv     |


**TimeNormalizationRunner**  
Normalizes time expressions from the document text or document concepts.  
Uses the HealthNLP [hnlp-timenorm](https://github.com/HealthNLPorg/hnlp-timenorm) project for actual normalization.
