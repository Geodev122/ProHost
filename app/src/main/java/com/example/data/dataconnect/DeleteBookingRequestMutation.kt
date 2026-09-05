
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



public interface DeleteBookingRequestMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      ProspaceConnectorConnector,
      DeleteBookingRequestMutation.Data,
      DeleteBookingRequestMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val id: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val bookingRequest_delete: BookingRequestKey?,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "DeleteBookingRequest"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun DeleteBookingRequestMutation.ref(
  
    id: String,

  
  
): com.google.firebase.dataconnect.MutationRef<
    DeleteBookingRequestMutation.Data,
    DeleteBookingRequestMutation.Variables
  > =
  ref(
    
      DeleteBookingRequestMutation.Variables(
        id=id,
  
      )
    
  )

public suspend fun DeleteBookingRequestMutation.execute(

  
    
      id: String,

  

  ): com.google.firebase.dataconnect.MutationResult<
    DeleteBookingRequestMutation.Data,
    DeleteBookingRequestMutation.Variables
  > =
  ref(
    
      id=id,
  
    
  ).execute()


