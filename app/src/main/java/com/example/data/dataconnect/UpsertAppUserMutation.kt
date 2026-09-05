
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



public interface UpsertAppUserMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      ProspaceConnectorConnector,
      UpsertAppUserMutation.Data,
      UpsertAppUserMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val id: String,
  
    val email: String,
  
    val fullName: String,
  
    val role: String,
  
    val specialty: String,
  
    val phone: String,
  
    val affiliation: String,
  
    val syndicateNumber: String,
  
    val governorate: String,
  
    val isVerified: Boolean,
  
    val verificationStatus: String,
  
    val verificationTier: String,
  
    val verificationNotes: com.google.firebase.dataconnect.OptionalVariable<String?>,
  
    val trustScore: Int,
  
    val subscriptionExpiryMillis: com.google.firebase.dataconnect.OptionalVariable<Long?>,
  
    val ownerPackageTier: String,
  
    val ownerPackageExpiryMillis: com.google.firebase.dataconnect.OptionalVariable<Long?>,
  
    val paygListingsBoughtCount: Int,
  
  ) {
    
    
      
      @kotlin.DslMarker public annotation class BuilderDsl

      
      @BuilderDsl
      public interface Builder {
        public var id: String
        public var email: String
        public var fullName: String
        public var role: String
        public var specialty: String
        public var phone: String
        public var affiliation: String
        public var syndicateNumber: String
        public var governorate: String
        public var isVerified: Boolean
        public var verificationStatus: String
        public var verificationTier: String
        public var verificationNotes: String?
        public var trustScore: Int
        public var subscriptionExpiryMillis: Long?
        public var ownerPackageTier: String
        public var ownerPackageExpiryMillis: Long?
        public var paygListingsBoughtCount: Int
        
      }

      public companion object {
        
        @Suppress("NAME_SHADOWING")
        public fun build(
          id: String,email: String,fullName: String,role: String,specialty: String,phone: String,affiliation: String,syndicateNumber: String,governorate: String,isVerified: Boolean,verificationStatus: String,verificationTier: String,trustScore: Int,ownerPackageTier: String,paygListingsBoughtCount: Int,
          block_: Builder.() -> Unit
        ): Variables {
          var id= id
            var email= email
            var fullName= fullName
            var role= role
            var specialty= specialty
            var phone= phone
            var affiliation= affiliation
            var syndicateNumber= syndicateNumber
            var governorate= governorate
            var isVerified= isVerified
            var verificationStatus= verificationStatus
            var verificationTier= verificationTier
            var verificationNotes: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var trustScore= trustScore
            var subscriptionExpiryMillis: com.google.firebase.dataconnect.OptionalVariable<Long?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var ownerPackageTier= ownerPackageTier
            var ownerPackageExpiryMillis: com.google.firebase.dataconnect.OptionalVariable<Long?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var paygListingsBoughtCount= paygListingsBoughtCount
            

          return object : Builder {
            override var id: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { id = value_ }
              
            override var email: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { email = value_ }
              
            override var fullName: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { fullName = value_ }
              
            override var role: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { role = value_ }
              
            override var specialty: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { specialty = value_ }
              
            override var phone: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { phone = value_ }
              
            override var affiliation: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { affiliation = value_ }
              
            override var syndicateNumber: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { syndicateNumber = value_ }
              
            override var governorate: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { governorate = value_ }
              
            override var isVerified: Boolean
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { isVerified = value_ }
              
            override var verificationStatus: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { verificationStatus = value_ }
              
            override var verificationTier: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { verificationTier = value_ }
              
            override var verificationNotes: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { verificationNotes = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var trustScore: Int
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { trustScore = value_ }
              
            override var subscriptionExpiryMillis: Long?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { subscriptionExpiryMillis = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var ownerPackageTier: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { ownerPackageTier = value_ }
              
            override var ownerPackageExpiryMillis: Long?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { ownerPackageExpiryMillis = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var paygListingsBoughtCount: Int
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { paygListingsBoughtCount = value_ }
              
            
          }.apply(block_)
          .let {
            Variables(
              id=id,email=email,fullName=fullName,role=role,specialty=specialty,phone=phone,affiliation=affiliation,syndicateNumber=syndicateNumber,governorate=governorate,isVerified=isVerified,verificationStatus=verificationStatus,verificationTier=verificationTier,verificationNotes=verificationNotes,trustScore=trustScore,subscriptionExpiryMillis=subscriptionExpiryMillis,ownerPackageTier=ownerPackageTier,ownerPackageExpiryMillis=ownerPackageExpiryMillis,paygListingsBoughtCount=paygListingsBoughtCount,
            )
          }
        }
      }
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val appUser_upsert: AppUserKey,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "UpsertAppUser"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun UpsertAppUserMutation.ref(
  
    id: String,email: String,fullName: String,role: String,specialty: String,phone: String,affiliation: String,syndicateNumber: String,governorate: String,isVerified: Boolean,verificationStatus: String,verificationTier: String,trustScore: Int,ownerPackageTier: String,paygListingsBoughtCount: Int,

  
    block_: UpsertAppUserMutation.Variables.Builder.() -> Unit = {}
  
): com.google.firebase.dataconnect.MutationRef<
    UpsertAppUserMutation.Data,
    UpsertAppUserMutation.Variables
  > =
  ref(
    
      UpsertAppUserMutation.Variables.build(
        id=id,email=email,fullName=fullName,role=role,specialty=specialty,phone=phone,affiliation=affiliation,syndicateNumber=syndicateNumber,governorate=governorate,isVerified=isVerified,verificationStatus=verificationStatus,verificationTier=verificationTier,trustScore=trustScore,ownerPackageTier=ownerPackageTier,paygListingsBoughtCount=paygListingsBoughtCount,
  
    block_
      )
    
  )

public suspend fun UpsertAppUserMutation.execute(

  
    
      id: String,email: String,fullName: String,role: String,specialty: String,phone: String,affiliation: String,syndicateNumber: String,governorate: String,isVerified: Boolean,verificationStatus: String,verificationTier: String,trustScore: Int,ownerPackageTier: String,paygListingsBoughtCount: Int,

  
    block_: UpsertAppUserMutation.Variables.Builder.() -> Unit = {}

  ): com.google.firebase.dataconnect.MutationResult<
    UpsertAppUserMutation.Data,
    UpsertAppUserMutation.Variables
  > =
  ref(
    
      id=id,email=email,fullName=fullName,role=role,specialty=specialty,phone=phone,affiliation=affiliation,syndicateNumber=syndicateNumber,governorate=governorate,isVerified=isVerified,verificationStatus=verificationStatus,verificationTier=verificationTier,trustScore=trustScore,ownerPackageTier=ownerPackageTier,paygListingsBoughtCount=paygListingsBoughtCount,
  
    block_
    
  ).execute()


