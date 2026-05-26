echo "Use this build command only if you are not using CUDA."
echo "This does not work on all systems.  Using a system with a GPU is advised."
mvn clean -Pget-hnlp-timenorm
mvn package -Ppip-torch -Ppip-ctakes-pbj  -Ppip-pbj-llm-tools -Ppip-pbj-langgraph -Ppip-lg-timelines -Dcuda.version=cpu $@
