
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


import kotlinx.coroutines.flow.filterNotNull as _flow_filterNotNull
import kotlinx.coroutines.flow.map as _flow_map


public interface GetBookingRequestsQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      ProspaceConnectorConnector,
      GetBookingRequestsQuery.Data,
      Unit
    >
{
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val bookingRequests: List<BookingRequestsItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class BookingRequestsItem(
  
    val id: String,
  
    val spaceId: String,
  
    val spaceTitle: String,
  
    val spaceDistrict: String,
  
    val governorate: String,
  
    val ownerId: String,
  
    val ownerName: String,
  
    val ownerPhone: String,
  
    val practitionerId: String,
  
    val practitionerName: String,
  
    val practitionerEmail: String,
  
    val practitionerPhone: String,
  
    val practitionerSpecialty: String,
  
    val practitionerSyndicateNumber: String,
  
    val formulaJson: String,
  
    val startDate: String,
  
    val endDate: String,
  
    val selectedDays: List<String>,
  
    val selectedStartHour: String,
  
    val selectedEndHour: String,
  
    val selectedShift: String,
  
    val selectedDateTimeRange: String,
  
    val durationMonths: Int,
  
    val totalAmountUsd: Double,
  
    val clinicalNotes: String,
  
    val status: String,
  
    val createdAt: Long,
  
    val reviewedAt: Long?,
  
    val rejectionReason: String?,
  
    val isExternalPaymentSettled: Boolean,
  
    val subdivisionId: String?,
  
    val subdivisionName: String?,
  
    val selectedStrategy: String?,
  
  ) {
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetBookingRequests"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Unit> =
      kotlinx.serialization.serializer()
  }
}

public fun GetBookingRequestsQuery.ref(
  
): com.google.firebase.dataconnect.QueryRef<
    GetBookingRequestsQuery.Data,
    Unit
  > =
  ref(
    
      Unit
    
  )

public suspend fun GetBookingRequestsQuery.execute(

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetBookingRequestsQuery.Data,
    Unit
  > =
  ref(
    
  ).execute()


  public fun GetBookingRequestsQuery.flow(
    
    ): kotlinx.coroutines.flow.Flow<GetBookingRequestsQuery.Data> =
    ref(
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

