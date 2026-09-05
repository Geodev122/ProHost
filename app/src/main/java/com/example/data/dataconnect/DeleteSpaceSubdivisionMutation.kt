
@file:Suppress(
  "KotlinRedundantDiagnosticSuppress",
  "PropertyName",
  "MayBeConstant",
  "RedundantVisibilityModifier",
  "RedundantCompanionReference",
  "RemoveEmptyClassBody",
  "SpellCheckingInspection",
  "unused",
)

package com.example.data.dataconnect



public interface DeleteSpaceSubdivisionMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      ProspaceConnectorConnector,
      DeleteSpaceSubdivisionMutation.Data,
      DeleteSpaceSubdivisionMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val id: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val spaceSubdivision_delete: SpaceSubdivisionKey?,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "DeleteSpaceSubdivision"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun DeleteSpaceSubdivisionMutation.ref(
  
    id: String,

  
  
): com.google.firebase.dataconnect.MutationRef<
    DeleteSpaceSubdivisionMutation.Data,
    DeleteSpaceSubdivisionMutation.Variables
  > =
  ref(
    
      DeleteSpaceSubdivisionMutation.Variables(
        id=id,
  
      )
    
  )

public suspend fun DeleteSpaceSubdivisionMutation.execute(

  
    
      id: String,

  

  ): com.google.firebase.dataconnect.MutationResult<
    DeleteSpaceSubdivisionMutation.Data,
    DeleteSpaceSubdivisionMutation.Variables
  > =
  ref(
    
      id=id,
  
    
  ).execute()


