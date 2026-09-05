
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



public interface UpsertBookingRequestMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      ProspaceConnectorConnector,
      UpsertBookingRequestMutation.Data,
      UpsertBookingRequestMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
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
  
    val reviewedAt: com.google.firebase.dataconnect.OptionalVariable<Long?>,
  
    val rejectionReason: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val isExternalPaymentSettled: Boolean,
  
    val subdivisionId: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val subdivisionName: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val selectedStrategy: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
  ) {
    
    
      
      @kotlin.DslMarker public annotation class BuilderDsl

      
      @BuilderDsl
      public interface Builder {
        public var id: String
        public var spaceId: String
        public var spaceTitle: String
        public var spaceDistrict: String
        public var governorate: String
        public var ownerId: String
        public var ownerName: String
        public var ownerPhone: String
        public var practitionerId: String
        public var practitionerName: String
        public var practitionerEmail: String
        public var practitionerPhone: String
        public var practitionerSpecialty: String
        public var practitionerSyndicateNumber: String
        public var formulaJson: String
        public var startDate: String
        public var endDate: String
        public var selectedDays: List<String>
        public var selectedStartHour: String
        public var selectedEndHour: String
        public var selectedShift: String
        public var selectedDateTimeRange: String
        public var durationMonths: Int
        public var totalAmountUsd: Double
        public var clinicalNotes: String
        public var status: String
        public var createdAt: Long
        public var reviewedAt: Long?
        public var rejectionReason: String?
        public var isExternalPaymentSettled: Boolean
        public var subdivisionId: String?
        public var subdivisionName: String?
        public var selectedStrategy: String?
        
      }

      public companion object {
        
        @Suppress("NAME_SHADOWING")
        public fun build(
          id: String,spaceId: String,spaceTitle: String,spaceDistrict: String,governorate: String,ownerId: String,ownerName: String,ownerPhone: String,practitionerId: String,practitionerName: String,practitionerEmail: String,practitionerPhone: String,practitionerSpecialty: String,practitionerSyndicateNumber: String,formulaJson: String,startDate: String,endDate: String,selectedDays: List<String>,selectedStartHour: String,selectedEndHour: String,selectedShift: String,selectedDateTimeRange: String,durationMonths: Int,totalAmountUsd: Double,clinicalNotes: String,status: String,createdAt: Long,isExternalPaymentSettled: Boolean,
          block_: Builder.() -> Unit
        ): Variables {
          var id= id
            var spaceId= spaceId
            var spaceTitle= spaceTitle
            var spaceDistrict= spaceDistrict
            var governorate= governorate
            var ownerId= ownerId
            var ownerName= ownerName
            var ownerPhone= ownerPhone
            var practitionerId= practitionerId
            var practitionerName= practitionerName
            var practitionerEmail= practitionerEmail
            var practitionerPhone= practitionerPhone
            var practitionerSpecialty= practitionerSpecialty
            var practitionerSyndicateNumber= practitionerSyndicateNumber
            var formulaJson= formulaJson
            var startDate= startDate
            var endDate= endDate
            var selectedDays= selectedDays
            var selectedStartHour= selectedStartHour
            var selectedEndHour= selectedEndHour
            var selectedShift= selectedShift
            var selectedDateTimeRange= selectedDateTimeRange
            var durationMonths= durationMonths
            var totalAmountUsd= totalAmountUsd
            var clinicalNotes= clinicalNotes
            var status= status
            var createdAt= createdAt
            var reviewedAt: com.google.firebase.dataconnect.OptionalVariable<Long?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var rejectionReason: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var isExternalPaymentSettled= isExternalPaymentSettled
            var subdivisionId: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var subdivisionName: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var selectedStrategy: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            

          return object : Builder {
            override var id: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { id = value_ }
              
            override var spaceId: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { spaceId = value_ }
              
            override var spaceTitle: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { spaceTitle = value_ }
              
            override var spaceDistrict: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { spaceDistrict = value_ }
              
            override var governorate: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { governorate = value_ }
              
            override var ownerId: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { ownerId = value_ }
              
            override var ownerName: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { ownerName = value_ }
              
            override var ownerPhone: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { ownerPhone = value_ }
              
            override var practitionerId: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { practitionerId = value_ }
              
            override var practitionerName: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { practitionerName = value_ }
              
            override var practitionerEmail: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { practitionerEmail = value_ }
              
            override var practitionerPhone: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { practitionerPhone = value_ }
              
            override var practitionerSpecialty: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { practitionerSpecialty = value_ }
              
            override var practitionerSyndicateNumber: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { practitionerSyndicateNumber = value_ }
              
            override var formulaJson: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { formulaJson = value_ }
              
            override var startDate: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { startDate = value_ }
              
            override var endDate: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { endDate = value_ }
              
            override var selectedDays: List<String>
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { selectedDays = value_ }
              
            override var selectedStartHour: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { selectedStartHour = value_ }
              
            override var selectedEndHour: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { selectedEndHour = value_ }
              
            override var selectedShift: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { selectedShift = value_ }
              
            override var selectedDateTimeRange: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { selectedDateTimeRange = value_ }
              
            override var durationMonths: Int
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { durationMonths = value_ }
              
            override var totalAmountUsd: Double
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { totalAmountUsd = value_ }
              
            override var clinicalNotes: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { clinicalNotes = value_ }
              
            override var status: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { status = value_ }
              
            override var createdAt: Long
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { createdAt = value_ }
              
            override var reviewedAt: Long?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { reviewedAt = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var rejectionReason: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { rejectionReason = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var isExternalPaymentSettled: Boolean
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { isExternalPaymentSettled = value_ }
              
            override var subdivisionId: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { subdivisionId = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var subdivisionName: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { subdivisionName = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var selectedStrategy: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { selectedStrategy = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            
          }.apply(block_)
          .let {
            Variables(
              id=id,spaceId=spaceId,spaceTitle=spaceTitle,spaceDistrict=spaceDistrict,governorate=governorate,ownerId=ownerId,ownerName=ownerName,ownerPhone=ownerPhone,practitionerId=practitionerId,practitionerName=practitionerName,practitionerEmail=practitionerEmail,practitionerPhone=practitionerPhone,practitionerSpecialty=practitionerSpecialty,practitionerSyndicateNumber=practitionerSyndicateNumber,formulaJson=formulaJson,startDate=startDate,endDate=endDate,selectedDays=selectedDays,selectedStartHour=selectedStartHour,selectedEndHour=selectedEndHour,selectedShift=selectedShift,selectedDateTimeRange=selectedDateTimeRange,durationMonths=durationMonths,totalAmountUsd=totalAmountUsd,clinicalNotes=clinicalNotes,status=status,createdAt=createdAt,reviewedAt=reviewedAt,rejectionReason=rejectionReason,isExternalPaymentSettled=isExternalPaymentSettled,subdivisionId=subdivisionId,subdivisionName=subdivisionName,selectedStrategy=selectedStrategy,
            )
          }
        }
      }
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val bookingRequest_upsert: BookingRequestKey,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "UpsertBookingRequest"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun UpsertBookingRequestMutation.ref(
  
    id: String,spaceId: String,spaceTitle: String,spaceDistrict: String,governorate: String,ownerId: String,ownerName: String,ownerPhone: String,practitionerId: String,practitionerName: String,practitionerEmail: String,practitionerPhone: String,practitionerSpecialty: String,practitionerSyndicateNumber: String,formulaJson: String,startDate: String,endDate: String,selectedDays: List<String>,selectedStartHour: String,selectedEndHour: String,selectedShift: String,selectedDateTimeRange: String,durationMonths: Int,totalAmountUsd: Double,clinicalNotes: String,status: String,createdAt: Long,isExternalPaymentSettled: Boolean,

  
    block_: UpsertBookingRequestMutation.Variables.Builder.() -> Unit = {}
  
): com.google.firebase.dataconnect.MutationRef<
    UpsertBookingRequestMutation.Data,
    UpsertBookingRequestMutation.Variables
  > =
  ref(
    
      UpsertBookingRequestMutation.Variables.build(
        id=id,spaceId=spaceId,spaceTitle=spaceTitle,spaceDistrict=spaceDistrict,governorate=governorate,ownerId=ownerId,ownerName=ownerName,ownerPhone=ownerPhone,practitionerId=practitionerId,practitionerName=practitionerName,practitionerEmail=practitionerEmail,practitionerPhone=practitionerPhone,practitionerSpecialty=practitionerSpecialty,practitionerSyndicateNumber=practitionerSyndicateNumber,formulaJson=formulaJson,startDate=startDate,endDate=endDate,selectedDays=selectedDays,selectedStartHour=selectedStartHour,selectedEndHour=selectedEndHour,selectedShift=selectedShift,selectedDateTimeRange=selectedDateTimeRange,durationMonths=durationMonths,totalAmountUsd=totalAmountUsd,clinicalNotes=clinicalNotes,status=status,createdAt=createdAt,isExternalPaymentSettled=isExternalPaymentSettled,
  
    block_
      )
    
  )

public suspend fun UpsertBookingRequestMutation.execute(

  
    
      id: String,spaceId: String,spaceTitle: String,spaceDistrict: String,governorate: String,ownerId: String,ownerName: String,ownerPhone: String,practitionerId: String,practitionerName: String,practitionerEmail: String,practitionerPhone: String,practitionerSpecialty: String,practitionerSyndicateNumber: String,formulaJson: String,startDate: String,endDate: String,selectedDays: List<String>,selectedStartHour: String,selectedEndHour: String,selectedShift: String,selectedDateTimeRange: String,durationMonths: Int,totalAmountUsd: Double,clinicalNotes: String,status: String,createdAt: Long,isExternalPaymentSettled: Boolean,

  
    block_: UpsertBookingRequestMutation.Variables.Builder.() -> Unit = {}

  ): com.google.firebase.dataconnect.MutationResult<
    UpsertBookingRequestMutation.Data,
    UpsertBookingRequestMutation.Variables
  > =
  ref(
    
      id=id,spaceId=spaceId,spaceTitle=spaceTitle,spaceDistrict=spaceDistrict,governorate=governorate,ownerId=ownerId,ownerName=ownerName,ownerPhone=ownerPhone,practitionerId=practitionerId,practitionerName=practitionerName,practitionerEmail=practitionerEmail,practitionerPhone=practitionerPhone,practitionerSpecialty=practitionerSpecialty,practitionerSyndicateNumber=practitionerSyndicateNumber,formulaJson=formulaJson,startDate=startDate,endDate=endDate,selectedDays=selectedDays,selectedStartHour=selectedStartHour,selectedEndHour=selectedEndHour,selectedShift=selectedShift,selectedDateTimeRange=selectedDateTimeRange,durationMonths=durationMonths,totalAmountUsd=totalAmountUsd,clinicalNotes=clinicalNotes,status=status,createdAt=createdAt,isExternalPaymentSettled=isExternalPaymentSettled,
  
    block_
    
  ).execute()


