
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



public interface DeleteSpaceListingMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      ProspaceConnectorConnector,
      DeleteSpaceListingMutation.Data,
      DeleteSpaceListingMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val id: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val spaceListing_delete: SpaceListingKey?,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "DeleteSpaceListing"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun DeleteSpaceListingMutation.ref(
  
    id: String,

  
  
): com.google.firebase.dataconnect.MutationRef<
    DeleteSpaceListingMutation.Data,
    DeleteSpaceListingMutation.Variables
  > =
  ref(
    
      DeleteSpaceListingMutation.Variables(
        id=id,
  
      )
    
  )

public suspend fun DeleteSpaceListingMutation.execute(

  
    
      id: String,

  

  ): com.google.firebase.dataconnect.MutationResult<
    DeleteSpaceListingMutation.Data,
    DeleteSpaceListingMutation.Variables
  > =
  ref(
    
      id=id,
  
    
  ).execute()


