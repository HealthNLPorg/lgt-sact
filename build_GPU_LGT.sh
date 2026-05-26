echo "Add to the command -Dcuda.version=<version> where <version> is 126, 128, 130, or rocm72"
echo "On Linux you do not need to specify -Dcuda.version for cuda version 13.0"
mvn clean -Pget-hnlp-timenorm
mvn package -Ppip-cuda -Ppip-torch -Ppip-ctakes-pbj -Ppip-pbj-llm-tools -Ppip-pbj-langgraph -Ppip-lg-timelines $@
